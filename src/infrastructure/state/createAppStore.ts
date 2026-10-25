/** EntryTask RN：客户端状态与服务端资源缓存：管理请求订阅、会话隔离和写后失效。 */
import {
  configureStore,
  createSlice,
  type PayloadAction,
} from '@reduxjs/toolkit';
import { createApi, type BaseQueryFn } from '@reduxjs/toolkit/query';
import type { ApiRequest, NetworkPort } from '@platform/ports/NetworkPort';
import type { PageUiState, PageUiStatePort } from '@shared/domain/PageUiState';
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
  // 只保存轻量UI状态；资源响应仍只在RTK Query缓存中。
  const initial: Record<string, PageUiState> = {};
  const pages = createSlice({
    name: 'pages',
    initialState: initial,
    reducers: {
      update(
        state,
        action: PayloadAction<{ key: string; patch: Partial<PageUiState> }>,
      ) {
        state[action.payload.key] = {
          offset: 0,
          category: action.payload.key.endsWith(':1') ? 'all' : 'fund',
          ...state[action.payload.key],
          ...action.payload.patch,
        };
      },
      reset: () => initial,
    },
  });
  const store = configureStore({
    reducer: { [api.reducerPath]: api.reducer, pages: pages.reducer },
    middleware: getDefault => getDefault().concat(api.middleware),
    devTools: __DEV__,
  });
  let uiEpoch: number | undefined;
  return {
    store,
    api,
    network,
    pageState(epoch: number, tab: number): PageUiStatePort {
      uiEpoch = epoch;
      const key = `${epoch}:${tab}`;
      const fallback: PageUiState = {
        offset: 0,
        category: tab === 1 ? 'all' : 'fund',
      };
      store.dispatch(pages.actions.update({ key, patch: {} }));
      return {
        getSnapshot: () => store.getState().pages[key] ?? fallback,
        subscribe: listener => store.subscribe(listener),
        update: patch => {
          if (uiEpoch === epoch)
            store.dispatch(pages.actions.update({ key, patch }));
        },
      };
    },
    resetPages: () => {
      uiEpoch = undefined;
      store.dispatch(pages.actions.reset());
    },
    dispose: () => {
      store.dispatch(api.util.resetApiState());
    },
  };
}
export type AppStateStore = ReturnType<typeof createAppStore>;
