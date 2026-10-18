/** EntryTask RN：会员业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { MemberContent } from '../domain/Member';
import type {
  ContentItem,
  Identity,
  Checkin,
  Page,
} from '@features/services/public';

export type MemberViewModelSources = {
  content: ResourceSource<MemberContent>;
  summary: ResourceSource<Identity>;
  checkin: ResourceSource<Checkin>;
  activities: ResourceSource<Page<ContentItem>>;
  claims: ResourceSource<Page<ContentItem>>;
  energy: ResourceSource<Page<ContentItem> & { balance: number }>;
};
export class MemberViewModel {
  readonly content: ResourceViewModel<MemberContent>;
  readonly summary: ResourceViewModel<Identity>;
  readonly checkin: ResourceViewModel<Checkin>;
  readonly activities: ResourceViewModel<Page<ContentItem>>;
  readonly claims: ResourceViewModel<Page<ContentItem>>;
  readonly energy: ResourceViewModel<Page<ContentItem> & { balance: number }>;
  constructor(sources: MemberViewModelSources) {
    this.content = new ResourceViewModel(sources.content);
    this.summary = new ResourceViewModel(sources.summary);
    this.checkin = new ResourceViewModel(sources.checkin);
    this.activities = new ResourceViewModel(sources.activities);
    this.claims = new ResourceViewModel(sources.claims);
    this.energy = new ResourceViewModel(sources.energy);
  }
  dispose(): void {
    this.content.deactivate();
    this.summary.deactivate();
    this.checkin.deactivate();
    this.activities.deactivate();
    this.claims.deactivate();
    this.energy.deactivate();
  }
}
