export type TransportKind = 'nativeMock' | 'native' | 'rnHttp';
export type RequestContext = {
  requestId: string;
  flowId: string;
  spanId: string;
};
export type ApiRequest = {
  apiId: string;
  params?: Record<string, string | number>;
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
