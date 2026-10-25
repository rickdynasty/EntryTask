import type { PageUiStatePort } from '@shared/domain/PageUiState';
/** EntryTask RN：财富业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { WealthContent } from '../domain/Wealth';
import type { Asset, Page } from '@features/services/public';
import type { Product } from '../domain/Wealth';

export type WealthViewModelSources = {
  content: ResourceSource<WealthContent>;
  products: ResourceSource<Page<Product>>;
  assets: ResourceSource<Asset>;
};
export class WealthViewModel {
  readonly content: ResourceViewModel<WealthContent>;
  readonly products: ResourceViewModel<Page<Product>>;
  readonly assets: ResourceViewModel<Asset>;
  constructor(
    sources: WealthViewModelSources,
    readonly ui: PageUiStatePort,
    private readonly productSource: (
      category: string,
    ) => ResourceSource<Page<Product>>,
  ) {
    this.content = new ResourceViewModel(sources.content);
    this.products = new ResourceViewModel(sources.products);
    this.assets = new ResourceViewModel(sources.assets);
  }
  selectCategory(category: string): void {
    if (this.ui.getSnapshot().category === category) return;
    this.ui.update({ category });
    this.products.replace(this.productSource(category));
  }
  dispose(): void {
    this.content.deactivate();
    this.products.deactivate();
    this.assets.deactivate();
  }
}
