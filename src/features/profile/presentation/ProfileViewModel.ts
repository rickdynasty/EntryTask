/** EntryTask RN：我的业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { Marketing, FinancialOverview } from '../domain/Profile';
import type { Asset, Identity } from '@features/services/public';

export type ProfileViewModelSources = {
  content: ResourceSource<Marketing>;
  identity: ResourceSource<Identity>;
  assets: ResourceSource<Asset>;
  financial: ResourceSource<FinancialOverview>;
};
export class ProfileViewModel {
  readonly financial: ResourceViewModel<FinancialOverview>;
  readonly content: ResourceViewModel<Marketing>;
  readonly identity: ResourceViewModel<Identity>;
  readonly assets: ResourceViewModel<Asset>;
  constructor(sources: ProfileViewModelSources) {
    this.financial = new ResourceViewModel(sources.financial);
    this.content = new ResourceViewModel(sources.content);
    this.identity = new ResourceViewModel(sources.identity);
    this.assets = new ResourceViewModel(sources.assets);
  }
  dispose(): void {
    this.financial.deactivate();
    this.content.deactivate();
    this.identity.deactivate();
    this.assets.deactivate();
  }
}
