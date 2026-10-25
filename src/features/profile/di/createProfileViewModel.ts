/** EntryTask RN：我的业务域模块：负责业务域依赖组装，不跨域持有页面私有实现。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as validators from '@infra/network/generated/validators';
import { ProfileViewModel } from '../presentation/ProfileViewModel';
import type { FinancialOverview } from '../domain/Profile';
import type { Asset, Identity } from '@features/services/public';

export function createProfileViewModel(
  client: ResourceClient,
  epoch: number,
): ProfileViewModel {
  // 身份、资产和财务资源由Route按认证态订阅。
  return new ProfileViewModel({
    financial: client.source<FinancialOverview>(
      { apiId: 'profile.financialOverview' },
      validators.profile_financialOverview,
      epoch,
    ),
    identity: client.source<Identity>(
      { apiId: 'profile.summary' },
      validators.profile_summary,
      epoch,
    ),
    assets: client.source<Asset>(
      { apiId: 'asset.summary' },
      validators.asset_summary,
      epoch,
    ),
  });
}
