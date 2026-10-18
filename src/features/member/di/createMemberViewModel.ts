/** EntryTask RN：会员业务域模块：负责业务域依赖组装，不跨域持有页面私有实现。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as validators from '@infra/network/generated/validators';
import { MemberViewModel } from '../presentation/MemberViewModel';
import type { MemberContent } from '../domain/Member';
import type {
  ContentItem,
  Identity,
  Checkin,
  Page,
} from '@features/services/public';

export function createMemberViewModel(
  client: ResourceClient,
  epoch: number,
): MemberViewModel {
  // 会员内容与会话私有摘要、签到、权益、能量资源分别建源。
  return new MemberViewModel({
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
    activities: client.source<Page<ContentItem>>(
      { apiId: 'activity.list' },
      validators.activity_list,
      epoch,
    ),
    claims: client.source<Page<ContentItem>>(
      { apiId: 'claim.list' },
      validators.claim_list,
      epoch,
    ),
    energy: client.source<Page<ContentItem> & { balance: number }>(
      { apiId: 'energy.records' },
      validators.energy_records,
      epoch,
    ),
  });
}
