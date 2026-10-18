/** EntryTask RN：财富业务域模块：负责业务域依赖组装，不跨域持有页面私有实现。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as validators from '@infra/network/generated/validators';
import { WealthViewModel } from '../presentation/WealthViewModel';
import type { WealthContent } from '../domain/Wealth';
import type { Asset, ContentItem, Page } from '@features/services/public';
import type { Product } from '../domain/Wealth';

export function createWealthViewModel(
  client: ResourceClient,
  epoch: number,
): WealthViewModel {
  // 财富、资产和资讯分别绑定契约校验器，并共享调用方会话 epoch。
  const productSource = (category: string) =>
    client.source<Page<Product>>(
      {
        apiId: 'product.list',
        params: { category, page: 1, pageSize: 3, scene: 'wealth' },
      },
      validators.product_list,
      epoch,
    );
  return new WealthViewModel(
    {
      content: client.source<WealthContent>(
        { apiId: 'wealth.content' },
        validators.wealth_content,
        epoch,
      ),
      products: client.source<Page<Product>>(
        {
          apiId: 'product.list',
          params: { category: 'fund', page: 1, pageSize: 3, scene: 'wealth' },
        },
        validators.product_list,
        epoch,
      ),
      assets: client.source<Asset>(
        { apiId: 'asset.summary' },
        validators.asset_summary,
        epoch,
      ),
      articles: client.source<Page<ContentItem>>(
        { apiId: 'article.list' },
        validators.article_list,
        epoch,
      ),
    },
    productSource,
  );
}
