/** EntryTask RN：首页业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { HomePage } from '../domain/Home';
import type { Page } from '@features/services/public';
import type { Product } from '@features/wealth/public';

export type HomePageViewModelSources = {
  content: ResourceSource<HomePage>;
  products: ResourceSource<Page<Product>>;
};
export class HomePageViewModel {
  readonly content: ResourceViewModel<HomePage>;
  readonly products: ResourceViewModel<Page<Product>>;
  constructor(
    sources: HomePageViewModelSources,
    private readonly productSource: (
      category: string,
    ) => ResourceSource<Page<Product>>,
  ) {
    this.content = new ResourceViewModel(sources.content);
    this.products = new ResourceViewModel(sources.products);
  }
  selectCategory(category: string): void {
    this.products.replace(this.productSource(category));
  }
  dispose(): void {
    this.content.deactivate();
    this.products.deactivate();
  }
}
