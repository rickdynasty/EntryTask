/** EntryTask RN：Native 能力适配层：封装 Codegen 模块、校验桥接数据并转换为稳定 Port。 */
import type { ShellSnapshot, HostCommand } from '@platform/ports/HostPort';
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
  shell: ShellSnapshot;
};
export type SessionSnapshot = { authenticated: boolean; sessionEpoch: number };
export function decodeReply<T>(
  raw: string,
  guard: (value: unknown) => value is T,
): T {
  // Bridge 返回值先解码信封与 outcome，再执行调用方提供的运行时类型守卫。
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
      Array.isArray(value.capabilities) &&
      'shell' in value &&
      isShell(value.shell),
  );

export const isShell = (value: unknown): value is ShellSnapshot =>
  Boolean(
    value &&
      typeof value === 'object' &&
      'tabId' in value &&
      typeof value.tabId === 'number' &&
      value.tabId >= 1 &&
      value.tabId <= 4 &&
      'authenticated' in value &&
      typeof value.authenticated === 'boolean' &&
      'sessionEpoch' in value &&
      typeof value.sessionEpoch === 'number' &&
      'revision' in value &&
      typeof value.revision === 'number' &&
      'depth' in value &&
      typeof value.depth === 'number',
  );
export const nativeGateway = {
  observeShell(listener: (snapshot: ShellSnapshot) => void): () => void {
    if (!hostModule?.onHostEvent) {
      throw new AppError(
        'HOST_UPGRADE_REQUIRED',
        '原生版本过旧，请重新构建并安装应用',
      );
    }
    const subscription = hostModule.onHostEvent(raw => {
      try {
        // Native 事件是 JSON 字符串；只把完整合法的 Shell 快照交给状态层。
        const value: unknown = JSON.parse(raw);
        if (isShell(value)) {
          listener(value);
        }
      } catch {
        console.warn('[EntryTask:RN] host.event.invalid');
      }
    });
    return () => subscription?.remove();
  },
  async command(command: HostCommand): Promise<void> {
    if (!hostModule) {
      throw new AppError('NATIVE_HOST_UNAVAILABLE', '宿主能力不可用');
    }
    decodeReply(
      await hostModule.command(JSON.stringify(command)),
      (value): value is true => value === true,
    );
  },
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
