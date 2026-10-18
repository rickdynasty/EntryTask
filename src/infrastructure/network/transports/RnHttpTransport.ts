/** EntryTask RN：网络基础设施：按 API 策略选择传输通道、验证响应并归一错误。 */
import axios, { type AxiosInstance } from 'axios';
import { AppError } from '@shared/domain/AppError';
import type { Transport, TransportResponse } from '@platform/ports/NetworkPort';

export class RnHttpTransport implements Transport {
  private readonly client: AxiosInstance;
  constructor(client?: AxiosInstance) {
    this.client =
      client ??
      axios.create({
        timeout: 10000,
        responseType: 'text',
        transformResponse: value => value,
        validateStatus: () => true,
      });
  }
  async execute(
    endpoint: Parameters<Transport['execute']>[0],
    request: Parameters<Transport['execute']>[1],
    context: Parameters<Transport['execute']>[2],
    signal: AbortSignal,
  ): Promise<TransportResponse> {
    // 仅执行显式配置的公开 RN HTTP API，不携带银行认证凭证。
    if (!endpoint.baseUrl || endpoint.authScope !== 'none') {
      throw new AppError(
        'ENDPOINT_NOT_CONFIGURED',
        'RN HTTP 端点未配置',
        'configuration',
      );
    }
    // 这是显式配置的 RN HTTP 通道，不是 NativeModule 适配或失败兜底。
    try {
      const response = await this.client.request<string>({
        baseURL: endpoint.baseUrl,
        url: endpoint.path,
        method: endpoint.method,
        params: request.params,
        data: request.body,
        timeout: endpoint.timeoutMs,
        signal,
        headers: { 'X-Request-Id': context.requestId },
      });
      return {
        httpStatus: response.status,
        bodyText:
          typeof response.data === 'string'
            ? response.data
            : JSON.stringify(response.data),
      };
    } catch (error) {
      if (axios.isCancel(error)) {
        throw new AppError('CANCELLED', '已取消', 'cancelled');
      }
      if (axios.isAxiosError(error) && error.code === 'ECONNABORTED') {
        throw new AppError('TIMEOUT', '请求超时', 'timeout');
      }
      throw new AppError('NETWORK_UNAVAILABLE', '网络连接失败', 'network');
    }
  }
}
