import type {
  HomeClientState,
  HomeContent,
  HomeRepository,
  ProductPage,
  QuerySnapshot,
  ResourceSubscription,
} from '../domain/HomeRepository';
import type { LoggerPort } from '@platform/ports/LoggerPort';

export type HomeState = {
  content: QuerySnapshot<HomeContent>;
  products: QuerySnapshot<ProductPage>;
  selectedService: string | null;
};
const idle = <T>(): QuerySnapshot<T> => ({
  data: null,
  loading: false,
  error: null,
});
export class HomeViewModel {
  private state: HomeState = {
    content: idle(),
    products: idle(),
    selectedService: null,
  };
  private readonly listeners = new Set<() => void>();
  private content: ResourceSubscription<HomeContent> | undefined;
  private products: ResourceSubscription<ProductPage> | undefined;
  private disposables: (() => void)[] = [];
  constructor(
    private readonly repository: HomeRepository,
    private readonly client: HomeClientState,
    private readonly logger: LoggerPort,
  ) {}
  getSnapshot = (): HomeState => this.state;
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
  activate(): void {
    if (this.content) {
      return;
    }
    this.logger.log('info', 'home', 'page.entered', { pageId: 'H-01' });
    this.content = this.repository.content();
    this.products = this.repository.products(1);
    const update = () => {
      this.state = {
        content: this.content?.snapshot() ?? idle(),
        products: this.products?.snapshot() ?? idle(),
        selectedService: this.client.selectedService(),
      };
      this.listeners.forEach(listener => listener());
    };
    this.disposables = [
      this.content.subscribe(update),
      this.products.subscribe(update),
      this.client.subscribe(update),
    ];
    update();
  }
  refresh = (): void => {
    this.content?.refresh();
    this.products?.refresh();
  };
  selectService = (id: string | null): void => {
    this.client.selectService(id);
    this.logger.log('info', 'home', 'service.selected', { pageId: 'H-01' });
  };
  deactivate(): void {
    this.disposables.forEach(dispose => dispose());
    this.disposables = [];
    this.content?.release();
    this.products?.release();
    this.content = undefined;
    this.products = undefined;
    this.logger.log('info', 'home', 'page.left', { pageId: 'H-01' });
  }
}
