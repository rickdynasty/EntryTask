/** EntryTask RN：客户端状态与服务端资源缓存：管理请求订阅、会话隔离和写后失效。 */
import type { ApiRequest } from '@platform/ports/NetworkPort';
import type { ResourceSource } from '@shared/domain/Resource';
import type { AppStateStore } from './createAppStore';
import { AppError } from '@shared/domain/AppError';

export function createResourceClient(runtime: AppStateStore) {
  const { store } = runtime;
  const queries = runtime.api.injectEndpoints({
    endpoints: builder => ({
      pageResource: builder.query<unknown, ApiRequest & { scope: number }>({
        query: request => request,
        providesTags: ['Resource'],
      }),
    }),
  });
  const owners = new Map<string, number>();
  return {
    source<T>(
      request: ApiRequest,
      validate: (value: unknown) => boolean,
      scope = 0,
    ): ResourceSource<T> {
      const params = Object.fromEntries(
        Object.entries(request.params ?? {}).sort(([a], [b]) =>
          a.localeCompare(b),
        ),
      );
      // 将参数排序后形成稳定缓存键，并把会话 epoch 纳入隔离范围。
      const key = JSON.stringify([request.apiId, params, scope]);
      const arg = { ...request, params, scope };
      return {
        open() {
          // 多个消费者共享同一查询；只有最后一个消费者离开时才中止请求。
          owners.set(key, (owners.get(key) ?? 0) + 1);
          const handle = store.dispatch(
            queries.endpoints.pageResource.initiate(arg, {
              forceRefetch: true,
            }),
          );
          let released = false;
          let checkedData: unknown;
          let dataValid = false;
          return {
            snapshot() {
              const query = queries.endpoints.pageResource.select(arg)(
                store.getState(),
              );
              // 同一份响应只校验一次，坏数据以错误状态暴露而不进入页面模型。
              if (query.data !== checkedData) {
                checkedData = query.data;
                dataValid = query.data !== undefined && validate(query.data);
              }
              const valid = dataValid;
              return {
                data: valid ? (query.data as T) : null,
                loading:
                  query.status === 'pending' ||
                  query.status === 'uninitialized',
                error: query.isError
                  ? 'error' in (query.error ?? {})
                    ? '数据暂不可用，请重试'
                    : '数据加载失败，请重试'
                  : query.data !== undefined && !valid
                  ? '接口数据格式无效'
                  : null,
              };
            },
            subscribe: listener => store.subscribe(listener),
            refresh: () => {
              if (!released) {
                void handle.refetch();
              }
            },
            release() {
              if (released) {
                return;
              }
              released = true;
              const count = (owners.get(key) ?? 1) - 1;
              if (count === 0) {
                owners.delete(key);
                handle.abort();
              } else {
                owners.set(key, count);
              }
              handle.unsubscribe();
            },
          };
        },
      };
    },
    async read<T>(
      request: ApiRequest,
      validate: (value: unknown) => boolean,
      signal: AbortSignal,
    ): Promise<T> {
      const data = await runtime.network.execute(request, signal);
      if (!validate(data))
        throw new AppError('INVALID_RESPONSE', '接口数据格式无效');
      return data as T;
    },
    async mutate<T>(
      request: ApiRequest,
      validate: (value: unknown) => boolean,
      signal: AbortSignal,
    ): Promise<T> {
      // 先等待 Native 写入成为事实，再失效查询，不乐观修改金融/权益数值。
      const data = await runtime.network.execute(request, signal);
      if (!validate(data)) {
        throw new AppError('INVALID_RESPONSE', '接口数据格式无效');
      }
      // Native 写入成为权威结果后再失效查询，不在 RN 预造余额。
      store.dispatch(queries.util.invalidateTags(['Resource']));
      return data as T;
    },
    reset() {
      store.dispatch(queries.util.resetApiState());
    },
  };
}
export type ResourceClient = ReturnType<typeof createResourceClient>;
