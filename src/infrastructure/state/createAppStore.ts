/** EntryTask RN：客户端状态与服务端资源缓存：管理请求订阅、会话隔离和写后失效。 */
import { configureStore } from '@reduxjs/toolkit';
import { createApi, type BaseQueryFn } from '@reduxjs/toolkit/query';
import type { ApiRequest, NetworkPort } from '@platform/ports/NetworkPort';
import { normalizeError } from '@shared/domain/AppError';

export function createAppStore(network: NetworkPort) {
  const baseQuery: BaseQueryFn<
    ApiRequest,
    unknown,
    { code: string; message: string }
  > = async (request, api) => {
    try {
      return { data: await network.execute(request, api.signal) };
    } catch (error) {
      const safe = normalizeError(error);
      return { error: { code: safe.code, message: safe.message } };
    }
  };
  // 每个 RN runtime 只创建一个查询服务；业务域通过注入端点复用该 Store。
  const api = createApi({
    reducerPath: 'resources',
    tagTypes: ['Resource'],
    baseQuery,
    keepUnusedDataFor: 60,
    endpoints: () => ({}),
  });
  const store = configureStore({
    reducer: { [api.reducerPath]: api.reducer },
    middleware: getDefault => getDefault().concat(api.middleware),
    devTools: __DEV__,
  });
  return {
    store,
    api,
    network,
    dispose: () => {
      store.dispatch(api.util.resetApiState());
    },
  };
}
export type AppStateStore = ReturnType<typeof createAppStore>;
