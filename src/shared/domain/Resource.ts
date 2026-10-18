/** EntryTask RN：共享领域契约：定义跨层可复用的纯数据或错误类型。 */
export type ResourceState<T> = {
  data: T | null;
  loading: boolean;
  error: string | null;
};
export interface ResourceSubscription<T> {
  snapshot(): ResourceState<T>;
  subscribe(listener: () => void): () => void;
  refresh(): void;
  release(): void;
}
export interface ResourceSource<T> {
  open(): ResourceSubscription<T>;
}
