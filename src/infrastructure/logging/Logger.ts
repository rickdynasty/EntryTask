/** EntryTask RN：RN 结构化日志实现：控制日志字段、缓存容量、Console 输出和 Native 投递。 */
import type {
  LogEvent,
  LogFields,
  LoggerPort,
  LogLevel,
} from '@platform/ports/LoggerPort';

let sequence = 0;
export const createId = (): string =>
  `${Date.now().toString(36)}-${(++sequence).toString(36)}`;
const allowed = new Set([
  'apiId',
  'flowId',
  'requestId',
  'spanId',
  'parentSpanId',
  'transport',
  'durationMs',
  'code',
  'pageId',
]);
export type LogSink = (events: readonly LogEvent[]) => Promise<void>;

export class Logger implements LoggerPort {
  private readonly events: LogEvent[] = [];
  private readonly queue: LogEvent[] = [];
  private flushing = false;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private sink: LogSink | undefined;
  droppedCount = 0;
  private sinkFailureReported = false;

  constructor(private readonly debug: boolean) {}

  setSink(sink: LogSink): void {
    this.sink = sink;
    this.schedule();
  }

  log(
    level: LogLevel,
    module: string,
    event: string,
    fields: LogFields = {},
  ): void {
    if (!this.debug && level === 'debug') {
      return;
    }
    // 只接受明细白名单字段，拒绝错误对象、请求对象和 Redux 状态。
    const safe = Object.fromEntries(
      Object.entries(fields)
        .filter(([key]) => allowed.has(key))
        .map(([key, value]) => [
          key,
          typeof value === 'string' ? value.slice(0, 160) : value,
        ]),
    );
    const entry: LogEvent = {
      eventId: createId(),
      layer: 'rn',
      level,
      module,
      event,
      timestamp: Date.now(),
      ...safe,
    };
    this.events.push(entry);
    if (this.events.length > 200) {
      this.events.shift();
    }
    if (this.debug || level === 'warn' || level === 'error') {
      const output =
        level === 'error'
          ? console.error
          : level === 'warn'
          ? console.warn
          : console.info;
      output('[EntryTask:RN]', JSON.stringify(entry));
    }
    if (level !== 'debug') {
      if (this.queue.length < 200) {
        this.queue.push(entry);
      } else {
        this.droppedCount++;
      }
      this.schedule();
    }
  }

  snapshot(): readonly LogEvent[] {
    return [...this.events];
  }

  dispose(): void {
    if (this.timer) {
      clearTimeout(this.timer);
    }
    this.queue.length = 0;
    this.sink = undefined;
  }

  private schedule(): void {
    if (!this.sink || this.flushing || this.timer || !this.queue.length) {
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = undefined;
      void this.flush();
    }, 200);
  }

  async flush(): Promise<void> {
    if (!this.sink || this.flushing || !this.queue.length) {
      return;
    }
    this.flushing = true;
    const batch = this.queue.splice(0, 20);
    // 批量异步投递并限制单批大小；Native 不可用时日志仍留在 RN Console。
    try {
      await this.sink(batch);
      this.sinkFailureReported = false;
    } catch {
      this.droppedCount += batch.length;
      // 桥接投递失败时不递归重试，也不输出原始拒绝原因。
      if (!this.sinkFailureReported) {
        console.warn(
          '[EntryTask:RN] telemetry.sink.failed: Native 日志投递失败，请查看 RN Console / ReactNativeJS',
        );
        this.sinkFailureReported = true;
      }
    } finally {
      this.flushing = false;
      this.schedule();
    }
  }
}
