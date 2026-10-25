/** EntryTask RN：会员业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { CheckinRepository } from '@features/services/public';
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
  private operation = { submitting: false, message: '' };
  private readonly listeners = new Set<() => void>();
  private abort: AbortController | undefined;
  private intent = 0;
  private key = '';
  private date = '';
  private confirmationRequired = false;
  private lastCheckedDate = '';
  getOperation = () => this.operation;
  subscribeOperation = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
  private report(submitting: boolean, message: string) {
    this.operation = { submitting, message };
    this.listeners.forEach(listener => listener());
  }
  constructor(
    sources: MemberViewModelSources,
    private readonly commands: CheckinRepository,
  ) {
    this.content = new ResourceViewModel(sources.content);
    this.summary = new ResourceViewModel(sources.summary);
    this.checkin = new ResourceViewModel(sources.checkin);
    this.activities = new ResourceViewModel(sources.activities);
    this.claims = new ResourceViewModel(sources.claims);
    this.energy = new ResourceViewModel(sources.energy);
  }
  /** 超时/失败先确认状态；未确认期间禁止再次写入，不自动重放。 */
  submit = async (): Promise<void> => {
    if (this.operation.submitting) return;
    const snapshot = this.checkin.getSnapshot();
    if (snapshot.loading) return;
    const status = snapshot.data;
    if (
      !status ||
      status.checkedToday ||
      this.lastCheckedDate === status.businessDate
    )
      return;
    if (this.date !== status.businessDate) {
      this.date = status.businessDate;
      this.key = `checkin-${Date.now()}-${++this.intent}`;
    }
    const abort = new AbortController();
    this.abort = abort;
    this.report(
      true,
      this.confirmationRequired ? '正在确认签到结果' : '正在签到',
    );
    try {
      if (this.confirmationRequired) {
        const confirmed = await this.commands.status(abort.signal);
        if (abort.signal.aborted) return;
        this.confirmationRequired = false;
        if (confirmed.checkedToday)
          this.lastCheckedDate = confirmed.businessDate;
        this.report(
          false,
          confirmed.checkedToday ? '已确认今日签到成功' : '暂未签到，可重试',
        );
        return;
      }
      const result = await this.commands.submit(
        this.date,
        this.key,
        abort.signal,
      );
      if (!abort.signal.aborted) {
        this.lastCheckedDate = result.businessDate;
        this.report(
          false,
          result.alreadyChecked
            ? '今日已签到'
            : `签到成功，本次获得${result.awardedEnergy}能量值`,
        );
      }
    } catch {
      this.confirmationRequired = true;
      if (abort.signal.aborted) return;
      this.report(true, '结果待确认，正在查询签到状态');
      try {
        const result = await this.commands.status(abort.signal);
        if (!abort.signal.aborted) {
          this.confirmationRequired = false;
          if (result.checkedToday) this.lastCheckedDate = result.businessDate;
          this.report(
            false,
            result.checkedToday ? '已确认今日签到成功' : '暂未签到，可重试',
          );
        }
      } catch {
        if (!abort.signal.aborted)
          this.report(false, '结果尚未确认，请再次点击以确认状态');
      }
    } finally {
      if (this.abort === abort) {
        this.abort = undefined;
        this.checkin.refresh();
        this.summary.refresh();
      }
    }
  };
  deactivateSubmission(): void {
    if (this.abort) this.confirmationRequired = true;
    this.abort?.abort();
    this.abort = undefined;
    this.report(false, '');
  }
  dispose(): void {
    this.deactivateSubmission();
    this.content.deactivate();
    this.summary.deactivate();
    this.checkin.deactivate();
    this.activities.deactivate();
    this.claims.deactivate();
    this.energy.deactivate();
  }
}
