/** EntryTask RN：平台无关 Port 契约：描述业务层可调用的宿主或基础设施能力。 */
export type TransportKind = 'nativeMock' | 'native' | 'rnHttp';
export type RequestContext = {
  requestId: string;
  flowId: string;
  spanId: string;
};
export type ApiRequest = {
  apiId: string;
  params?: Record<string, string | number>;
  body?: Record<string, string | number | boolean>;
};
export type EndpointPolicy = {
  apiId: string;
  method: string;
  path: string;
  transport: TransportKind;
  authScope: 'none' | 'nativeBank';
  timeoutMs: number;
  policyRevision: number;
  baseUrl?: string;
  responseSchema?: string;
};
export type TransportResponse = { httpStatus: number; bodyText: string };
export interface Transport {
  execute(
    endpoint: EndpointPolicy,
    request: ApiRequest,
    context: RequestContext,
    signal: AbortSignal,
  ): Promise<TransportResponse>;
}
export interface NetworkPort {
  execute(request: ApiRequest, signal: AbortSignal): Promise<unknown>;
}
