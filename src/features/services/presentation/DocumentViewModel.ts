/** EntryTask RN：通用服务与详情业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import type { Document } from '../domain/Document';
import type { ResourceSource } from '@shared/domain/Resource';
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import { normalizeError } from '@shared/domain/AppError';
import type { LoggerPort } from '@platform/ports/LoggerPort';
export class DocumentViewModel {
  readonly resource: ResourceViewModel<Document>;
  private state = { busy: false, error: '', notice: '' };
  private keyword = '';
  private scope = 'services';
  private listeners = new Set<() => void>();
  private controller: AbortController | undefined;
  constructor(
    private readonly load: (
      page: number,
      keyword: string,
      scope: string,
    ) => ResourceSource<Document>,
    private readonly write:
      | ((date: string, signal: AbortSignal) => Promise<void>)
      | undefined,
    private readonly logger: LoggerPort,
    private readonly pageId: string,
  ) {
    this.resource = new ResourceViewModel(load(1, '', 'services'));
  }
  getSnapshot = () => this.state;
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
  search = (keyword: string, scope: string): void => {
    // 日志只记录公开的筛选类别，不记录用户输入的搜索关键词。
    this.logger.log('info', 'search', 'search.submitted', { pageId: scope });
    this.keyword = keyword;
    this.scope = scope;
    this.resource.replace(this.load(1, keyword, scope));
  };
  page = (page: number): void => {
    this.logger.log('info', 'services', 'list.page.requested', {
      pageId: String(page),
    });
    this.resource.replace(this.load(page, this.keyword, this.scope));
  };
  submit = (): void => {
    if (this.state.busy || !this.write) return;
    this.controller = new AbortController();
    const signal = this.controller.signal;
    this.state = { busy: true, error: '', notice: '' };
    // 写入前上报阶段；幂等键和业务参数不进入日志。
    this.logger.log('info', 'services', 'action.submitted', {
      pageId: this.pageId,
    });
    this.emit();
    void this.write(
      this.resource.getSnapshot().data?.businessDate ?? '',
      signal,
    )
      .then(() => {
        if (!signal.aborted) {
          this.logger.log('info', 'services', 'action.completed', {
            pageId: this.pageId,
          });
          this.state = {
            busy: false,
            error: '',
            notice: '操作成功，结果已由 Native 保存',
          };
          this.resource.refresh();
          this.emit();
        }
      })
      .catch(reason => {
        if (!signal.aborted) {
          const safe = normalizeError(reason);
          this.logger.log('warn', 'services', 'action.failed', {
            code: safe.code,
            pageId: this.pageId,
          });
          this.state = {
            busy: false,
            error: '操作未完成，请重试；结果以重新查询为准',
            notice: '',
          };
          this.emit();
        }
      });
  };
  private emit() {
    this.listeners.forEach(listener => listener());
  }
  dispose = (): void => {
    this.controller?.abort();
    this.resource.deactivate();
  };
}
