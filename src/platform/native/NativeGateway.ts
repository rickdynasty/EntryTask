import { AppError } from '@shared/domain/AppError';
import runtimeContract from '@contracts/bridge/runtime.json';
import {
  hostModule,
  sessionModule,
  storageModule,
  telemetryModule,
} from './modules';
import type { LogEvent } from '@platform/ports/LoggerPort';

export type HostSnapshot = {
  protocolVersion: string;
  bootId: string;
  componentName: string;
  capabilities: string[];
};
export type SessionSnapshot = { authenticated: boolean; sessionEpoch: number };
export function decodeReply<T>(
  raw: string,
  guard: (value: unknown) => value is T,
): T {
  let reply: unknown;
  try {
    reply = JSON.parse(raw);
  } catch {
    throw new AppError('INVALID_BRIDGE_REPLY', '原生能力响应不是有效 JSON');
  }
  if (!reply || typeof reply !== 'object' || !('outcome' in reply)) {
    throw new AppError('INVALID_BRIDGE_REPLY', '原生能力响应无效');
  }
  if (reply.outcome !== 'ok') {
    const error =
      'error' in reply && reply.error && typeof reply.error === 'object'
        ? reply.error
        : {};
    const code = 'code' in error ? String(error.code) : 'NATIVE_ERROR';
    throw new AppError(
      code,
      code === 'HOST_RESOURCE_MISSING'
        ? '应用缺少运行资源，请重新构建并安装应用'
        : code === 'PROTOCOL_VERSION_MISMATCH'
        ? '应用与页面版本不一致，请重新构建并安装应用'
        : '原生能力暂不可用',
      code === 'CANCELLED'
        ? 'cancelled'
        : code === 'TIMEOUT'
        ? 'timeout'
        : 'unavailable',
    );
  }
  const data: unknown = 'data' in reply ? reply.data : undefined;
  if (!guard(data)) {
    throw new AppError('INVALID_BRIDGE_DATA', '原生能力数据无效');
  }
  return data;
}
export const isHost = (value: unknown): value is HostSnapshot =>
  Boolean(
    value &&
      typeof value === 'object' &&
      'protocolVersion' in value &&
      value.protocolVersion === runtimeContract.protocolVersion &&
      'componentName' in value &&
      value.componentName === runtimeContract.componentName &&
      'bootId' in value &&
      typeof value.bootId === 'string' &&
      'capabilities' in value &&
      Array.isArray(value.capabilities),
  );

export const nativeGateway = {
  async bootstrap(flowId?: string): Promise<HostSnapshot> {
    if (!hostModule) {
      throw new AppError(
        'NATIVE_HOST_UNAVAILABLE',
        '当前平台尚未接入 NativeHost',
        'unavailable',
      );
    }
    return decodeReply(
      await hostModule.bootstrap(
        JSON.stringify({
          protocolVersion: runtimeContract.protocolVersion,
          flowId,
        }),
      ),
      isHost,
    );
  },
  async session(): Promise<SessionSnapshot> {
    if (!sessionModule) {
      throw new AppError(
        'NATIVE_SESSION_UNAVAILABLE',
        '会话能力不可用',
        'unavailable',
      );
    }
    return decodeReply(
      await sessionModule.getSnapshot('{}'),
      (v): v is SessionSnapshot =>
        Boolean(
          v &&
            typeof v === 'object' &&
            'authenticated' in v &&
            typeof v.authenticated === 'boolean' &&
            'sessionEpoch' in v &&
            typeof v.sessionEpoch === 'number',
        ),
    );
  },
  async readVisibility(): Promise<boolean> {
    if (!storageModule) {
      throw new AppError(
        'NATIVE_STORAGE_UNAVAILABLE',
        '存储能力不可用',
        'unavailable',
      );
    }
    return decodeReply(
      await storageModule.read(JSON.stringify({ key: 'assetVisibility' })),
      (v): v is boolean => typeof v === 'boolean',
    );
  },
  async writeVisibility(value: boolean): Promise<void> {
    if (!storageModule) {
      throw new AppError(
        'NATIVE_STORAGE_UNAVAILABLE',
        '存储能力不可用',
        'unavailable',
      );
    }
    decodeReply(
      await storageModule.write(
        JSON.stringify({ key: 'assetVisibility', value }),
      ),
      (v): v is boolean => v === true,
    );
  },
  async emitLogs(events: readonly LogEvent[]): Promise<void> {
    if (!telemetryModule) {
      throw new AppError(
        'NATIVE_TELEMETRY_UNAVAILABLE',
        '日志能力不可用',
        'unavailable',
      );
    }
    decodeReply(
      await telemetryModule.emitBatch(JSON.stringify(events)),
      (v): v is boolean => v === true,
    );
  },
};
