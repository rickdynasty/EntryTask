/** EntryTask RN：网络基础设施：按 API 策略选择传输通道、验证响应并归一错误。 */
import * as generatedValidators from './generated/validators';
import { AppError, normalizeError } from '@shared/domain/AppError';
import type {
  ApiRequest,
  NetworkPort,
  Transport,
  TransportKind,
} from '@platform/ports/NetworkPort';
import type { LoggerPort } from '@platform/ports/LoggerPort';
import { createId } from '@infra/logging/Logger';
import { EndpointRegistry } from './EndpointRegistry';

export class TransportRouter implements NetworkPort {
  constructor(
    private readonly registry: EndpointRegistry,
    private readonly transports: Readonly<Record<TransportKind, Transport>>,
    private readonly logger: LoggerPort,
  ) {}
  async execute(request: ApiRequest, signal: AbortSignal): Promise<unknown> {
    // 先解析静态 API 策略，再按指定通道执行，不在失败后切换传输方式。
    const endpoint = this.registry.resolve(request.apiId);
    const context = {
      requestId: createId(),
      flowId: createId(),
      spanId: createId(),
    };
    const fields = {
      ...context,
      apiId: request.apiId,
      transport: endpoint.transport,
    };
    const start = Date.now();
    this.logger.log('info', 'network', 'request.started', fields);
    try {
      const response = await this.transports[endpoint.transport].execute(
        endpoint,
        request,
        context,
        signal,
      );
      if (response.httpStatus < 200 || response.httpStatus >= 300) {
        throw new AppError(
          `HTTP_${response.httpStatus}`,
          '请求未完成，请重试',
          'http',
        );
      }
      let body: unknown;
      try {
        body = JSON.parse(response.bodyText);
      } catch {
        throw new AppError('INVALID_JSON', '接口响应格式无效');
      }
      if (
        !body ||
        typeof body !== 'object' ||
        !('code' in body) ||
        !('data' in body)
      ) {
        throw new AppError('INVALID_RESPONSE', '接口响应结构无效');
      }
      // 统一验证 HTTP、信封结构、业务码与生成的响应 Schema。
      if (body.code !== 200) {
        throw new AppError(String(body.code), '操作未完成，请重试', 'business');
      }
      const validators: Record<string, (value: unknown) => boolean> =
        generatedValidators;
      const validate = validators[request.apiId.replaceAll('.', '_')];
      if (endpoint.responseSchema && (!validate || !validate(body.data))) {
        throw new AppError('INVALID_RESPONSE_SCHEMA', '接口数据格式无效');
      }
      this.logger.log('info', 'network', 'request.completed', {
        ...fields,
        durationMs: Date.now() - start,
      });
      return body.data;
    } catch (error) {
      const safe = normalizeError(error);
      this.logger.log(
        safe.kind === 'cancelled' ? 'info' : 'warn',
        'network',
        safe.kind === 'cancelled' ? 'request.cancelled' : 'request.failed',
        { ...fields, code: safe.code, durationMs: Date.now() - start },
      );
      throw safe;
    }
  }
}
