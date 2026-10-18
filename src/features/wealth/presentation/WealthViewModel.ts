/** EntryTask RN：财富业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { WealthContent } from '../domain/Wealth';
import type { Asset, ContentItem, Page } from '@features/services/public';
import type { Product } from '../domain/Wealth';

export type WealthViewModelSources = {
  content: ResourceSource<WealthContent>;
  products: ResourceSource<Page<Product>>;
  assets: ResourceSource<Asset>;
  articles: ResourceSource<Page<ContentItem>>;
};
export class WealthViewModel {
  readonly content: ResourceViewModel<WealthContent>;
  readonly products: ResourceViewModel<Page<Product>>;
  readonly assets: ResourceViewModel<Asset>;
  readonly articles: ResourceViewModel<Page<ContentItem>>;
  constructor(
    sources: WealthViewModelSources,
    private readonly productSource: (
      category: string,
    ) => ResourceSource<Page<Product>>,
  ) {
    this.content = new ResourceViewModel(sources.content);
    this.products = new ResourceViewModel(sources.products);
    this.assets = new ResourceViewModel(sources.assets);
    this.articles = new ResourceViewModel(sources.articles);
  }
  selectCategory(category: string): void {
    this.products.replace(this.productSource(category));
  }
  dispose(): void {
    this.content.deactivate();
    this.products.deactivate();
    this.assets.deactivate();
    this.articles.deactivate();
  }
}
