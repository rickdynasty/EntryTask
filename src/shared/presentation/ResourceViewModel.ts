/** EntryTask RN：共享呈现状态适配：连接资源订阅与 React 生命周期。 */
import type {
  ResourceSource,
  ResourceState,
  ResourceSubscription,
} from '@shared/domain/Resource';

// 仅持有查询缓存的视图快照，不再复制一份持久资源状态。
export class ResourceViewModel<T> {
  private state: ResourceState<T> = { data: null, loading: true, error: null };
  private resource: ResourceSubscription<T> | undefined;
  private unsubscribe: (() => void) | undefined;
  private listeners = new Set<() => void>();
  constructor(private source: ResourceSource<T>) {}
  getSnapshot = (): ResourceState<T> => this.state;
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
  activate = (): void => {
    if (this.resource) {
      return;
    }
    // 页面激活时才持有查询订阅；停用会释放当前资源句柄。
    this.resource = this.source.open();
    this.unsubscribe = this.resource.subscribe(this.update);
    this.update();
  };
  private update = (): void => {
    if (this.resource) {
      const next = this.resource.snapshot();
      if (
        next.data === this.state.data &&
        next.loading === this.state.loading &&
        next.error === this.state.error
      )
        return;
      this.state = next;
      this.emit();
    }
  };
  private emit(): void {
    this.listeners.forEach(listener => listener());
  }
  replace(source: ResourceSource<T>): void {
    // 分类或搜索条件变化时先释放旧资源，再绑定新请求的数据源。
    this.deactivate();
    this.source = source;
    this.state = { data: null, loading: true, error: null };
    this.activate();
  }
  refresh = (): void => {
    this.resource?.refresh();
  };
  deactivate = (): void => {
    // 清理可重复调用，保证 React 卸载与 Tab 隐藏不会遗留订阅。
    this.unsubscribe?.();
    this.unsubscribe = undefined;
    this.resource?.release();
    this.resource = undefined;
  };
  reset(): void {
    this.deactivate();
    this.state = { data: null, loading: true, error: null };
    this.emit();
  }
}
