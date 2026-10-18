/** EntryTask RN：网络基础设施：按 API 策略选择传输通道、验证响应并归一错误。 */
import catalog from '@contracts/api/endpoints.json';
import { AppError } from '@shared/domain/AppError';
import type { EndpointPolicy } from '@platform/ports/NetworkPort';

export class EndpointRegistry {
  private readonly endpoints: ReadonlyMap<string, EndpointPolicy>;
  constructor(endpoints: readonly EndpointPolicy[]) {
    this.endpoints = new Map(
      endpoints.map(endpoint => [
        endpoint.apiId,
        Object.freeze({ ...endpoint }),
      ]),
    );
  }
  resolve(apiId: string): EndpointPolicy {
    const endpoint = this.endpoints.get(apiId);
    if (!endpoint) {
      throw new AppError(
        'ENDPOINT_NOT_CONFIGURED',
        '接口尚未配置',
        'configuration',
      );
    }
    if (
      endpoint.transport === 'rnHttp' &&
      (!endpoint.baseUrl?.startsWith('https://') ||
        endpoint.authScope !== 'none')
    ) {
      throw new AppError(
        'INVALID_ENDPOINT_POLICY',
        'RN HTTP 服务或认证策略未配置',
        'configuration',
      );
    }
    return endpoint;
  }
}
export const defaultEndpointRegistry = new EndpointRegistry(
  catalog.endpoints as EndpointPolicy[],
);
