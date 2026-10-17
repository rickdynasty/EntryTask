import type { AppStateStore } from '@infra/state/createAppStore';
import {
  home_content,
  product_list,
} from '@infra/network/generated/validators';
import type {
  HomeContent,
  HomeRepository,
  ProductPage,
  ResourceSubscription,
} from '../domain/HomeRepository';

export function createHomeRepository(runtime: AppStateStore): HomeRepository {
  const { store } = runtime;
  const api = runtime.api.injectEndpoints({
    endpoints: builder => ({
      homeContent: builder.query<HomeContent, void>({
        query: () => ({ apiId: 'home.content' }),
        transformResponse: (data: unknown) => {
          if (!home_content(data)) {
            throw new Error('HOME_SCHEMA_INVALID');
          }
          return data as HomeContent;
        },
      }),
      products: builder.query<ProductPage, number>({
        query: page => ({
          apiId: 'product.list',
          params: { page, pageSize: 10, category: 'fund' },
        }),
        transformResponse: (data: unknown) => {
          if (!product_list(data)) {
            throw new Error('PRODUCT_SCHEMA_INVALID');
          }
          return data as ProductPage;
        },
      }),
    }),
  });
  function resource<T>(
    start: () => { unsubscribe(): void; refetch(): unknown },
    select: () => {
      data?: T | undefined;
      isLoading: boolean;
      isError: boolean;
    },
  ): ResourceSubscription<T> {
    const handle = start();
    let released = false;
    return {
      snapshot: () => {
        const value = select();
        return {
          data: value.data ?? null,
          loading: value.isLoading,
          error: value.isError ? '数据加载失败，请重试' : null,
        };
      },
      subscribe: listener => store.subscribe(listener),
      refresh: () => {
        if (!released) {
          handle.refetch();
        }
      },
      // unsubscribe does not abort a query another consumer still owns.
      release: () => {
        if (!released) {
          released = true;
          handle.unsubscribe();
        }
      },
    };
  }
  return {
    content: () =>
      resource<HomeContent>(
        () => store.dispatch(api.endpoints.homeContent.initiate()),
        () => api.endpoints.homeContent.select()(store.getState()),
      ),
    products: page =>
      resource<ProductPage>(
        () => store.dispatch(api.endpoints.products.initiate(page)),
        () => api.endpoints.products.select(page)(store.getState()),
      ),
  };
}
