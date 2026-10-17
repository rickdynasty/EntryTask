import {
  configureStore,
  createSlice,
  type PayloadAction,
} from '@reduxjs/toolkit';
import { createApi, type BaseQueryFn } from '@reduxjs/toolkit/query';
import type { ApiRequest, NetworkPort } from '@platform/ports/NetworkPort';
import { normalizeError } from '@shared/domain/AppError';

export type ClientState = { selectedService: string | null };
const initialState: ClientState = { selectedService: null };
const client = createSlice({
  name: 'homeClient',
  initialState,
  reducers: {
    selectService: (state, action: PayloadAction<string | null>) => {
      state.selectedService = action.payload;
    },
  },
});
export const clientActions = client.actions;
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
  // One service per runtime. Features inject endpoints without creating another store.
  const api = createApi({
    reducerPath: 'resources',
    baseQuery,
    keepUnusedDataFor: 60,
    endpoints: () => ({}),
  });
  const store = configureStore({
    reducer: { homeClient: client.reducer, [api.reducerPath]: api.reducer },
    middleware: getDefault => getDefault().concat(api.middleware),
    devTools: __DEV__,
  });
  return {
    store,
    api,
    dispose: () => {
      store.dispatch(api.util.resetApiState());
    },
  };
}
export type AppStateStore = ReturnType<typeof createAppStore>;
