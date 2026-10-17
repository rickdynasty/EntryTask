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
    if (!endpoint.baseUrl || endpoint.authScope !== 'none') {
      throw new AppError(
        'ENDPOINT_NOT_CONFIGURED',
        'RN HTTP 端点未配置',
        'configuration',
      );
    }
    // This is the standard RN HTTP path, not a NativeModule adapter or fallback.
    try {
      const response = await this.client.request<string>({
        baseURL: endpoint.baseUrl,
        url: endpoint.path,
        method: endpoint.method,
        params: request.params,
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
