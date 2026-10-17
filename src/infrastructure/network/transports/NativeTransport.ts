import { networkModule } from '@platform/native/modules';
import { decodeReply } from '@platform/native/NativeGateway';
import { AppError } from '@shared/domain/AppError';
import type { Transport, TransportResponse } from '@platform/ports/NetworkPort';

const isResponse = (v: unknown): v is TransportResponse =>
  Boolean(
    v &&
      typeof v === 'object' &&
      'httpStatus' in v &&
      typeof v.httpStatus === 'number' &&
      'bodyText' in v &&
      typeof v.bodyText === 'string',
  );
export class NativeTransport implements Transport {
  async execute(
    endpoint: Parameters<Transport['execute']>[0],
    request: Parameters<Transport['execute']>[1],
    context: Parameters<Transport['execute']>[2],
    signal: AbortSignal,
  ): Promise<TransportResponse> {
    const module = networkModule;
    if (!module) {
      throw new AppError(
        'NATIVE_NETWORK_UNAVAILABLE',
        '原生网络能力不可用',
        'unavailable',
      );
    }
    if (signal.aborted) {
      throw new AppError('CANCELLED', '已取消', 'cancelled');
    }
    const cancel = () => module.cancel(context.requestId);
    signal.addEventListener('abort', cancel);
    try {
      const raw = await module.execute(
        JSON.stringify({
          ...context,
          apiId: request.apiId,
          params: request.params ?? {},
          timeoutMs: endpoint.timeoutMs,
        }),
      );
      if (signal.aborted) {
        throw new AppError('CANCELLED', '已取消', 'cancelled');
      }
      return decodeReply(raw, isResponse);
    } finally {
      signal.removeEventListener('abort', cancel);
    }
  }
}
