import { createCheckinRepository } from '@features/services/public';
/** EntryTask RN：会员业务域模块：负责业务域依赖组装，不跨域持有页面私有实现。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as validators from '@infra/network/generated/validators';
import { MemberViewModel } from '../presentation/MemberViewModel';
import type { MemberContent } from '../domain/Member';
import type { Identity, Checkin } from '@features/services/public';

export function createMemberViewModel(
  client: ResourceClient,
  epoch: number,
): MemberViewModel {
  // 只为根页实际订阅的会员内容、摘要和签到建源；子页资源由各自流程管理。
  return new MemberViewModel(
    {
      content: client.source<MemberContent>(
        { apiId: 'member.content' },
        validators.member_content,
        epoch,
      ),
      summary: client.source<Identity>(
        { apiId: 'member.summary' },
        validators.member_summary,
        epoch,
      ),
      checkin: client.source<Checkin>(
        { apiId: 'checkin.status' },
        validators.checkin_status,
        epoch,
      ),
    },
    createCheckinRepository(client),
  );
}
