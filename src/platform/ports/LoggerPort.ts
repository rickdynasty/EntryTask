/** EntryTask RN：平台无关 Port 契约：描述业务层可调用的宿主或基础设施能力。 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';
export type LogFields = {
  apiId?: string;
  flowId?: string;
  requestId?: string;
  spanId?: string;
  parentSpanId?: string;
  transport?: string;
  durationMs?: number;
  code?: string;
  pageId?: string;
};
export type LogEvent = LogFields & {
  eventId: string;
  layer: 'rn';
  level: LogLevel;
  module: string;
  event: string;
  timestamp: number;
};
export interface LoggerPort {
  log(level: LogLevel, module: string, event: string, fields?: LogFields): void;
}
