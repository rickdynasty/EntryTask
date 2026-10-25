/** 通用详情与会员任务卡共用同一写适配，不直接访问NativeModule。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as validators from '@infra/network/generated/validators';
import type { CheckinRepository } from '../domain/CheckinRepository';
export function createCheckinRepository(
  client: ResourceClient,
): CheckinRepository {
  return {
    submit: (date, idempotencyKey, signal) =>
      client.mutate(
        {
          apiId: 'checkin.submit',
          body: { expectedBusinessDate: date, idempotencyKey },
        },
        validators.checkin_submit,
        signal,
      ),
    status: signal =>
      client.read(
        { apiId: 'checkin.status' },
        validators.checkin_status,
        signal,
      ),
  };
}
