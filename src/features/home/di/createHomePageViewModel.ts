/** EntryTask RN：首页业务域模块：负责业务域依赖组装，不跨域持有页面私有实现。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as validators from '@infra/network/generated/validators';
import { HomePageViewModel } from '../presentation/HomePageViewModel';
import type { HomePage } from '../domain/Home';
import type { Page } from '@features/services/public';
import type { Product } from '@features/wealth/public';

export function createHomePageViewModel(
  client: ResourceClient,
  epoch: number,
): HomePageViewModel {
  // 按首页场景创建内容与推荐商品资源，并为列表分页指定请求参数。
  const productSource = (category: string) =>
    client.source<Page<Product>>(
      {
        apiId: 'product.list',
        params: { category, page: 1, pageSize: 3, scene: 'home' },
      },
      validators.product_list,
      epoch,
    );
  return new HomePageViewModel(
    {
      content: client.source<HomePage>(
        { apiId: 'home.content' },
        validators.home_content,
        epoch,
      ),
      products: client.source<Page<Product>>(
        {
          apiId: 'product.list',
          params: { category: 'all', page: 1, pageSize: 3, scene: 'home' },
        },
        validators.product_list,
        epoch,
      ),
    },
    productSource,
  );
}
