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
    // Explicit allowlist, never serialize errors, request objects or Redux state.
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
    if (this.debug) {
      console.info('[EntryTask:RN]', JSON.stringify(entry));
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
    try {
      await this.sink(batch);
    } catch {
      this.droppedCount += batch.length;
    } finally {
      this.flushing = false;
      this.schedule();
    }
  }
}
