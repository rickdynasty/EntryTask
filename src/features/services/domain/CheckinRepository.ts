/** 签到命令契约；日期、幂等及账本事实由Native处理。 */
import type { Checkin } from './Content';
export interface CheckinRepository {
  submit(
    date: string,
    idempotencyKey: string,
    signal: AbortSignal,
  ): Promise<Checkin>;
  status(signal: AbortSignal): Promise<Checkin>;
}
