export type HomeContent = {
  title: string;
  subtitle: string;
  services: { id: string; title: string }[];
  isDemo: true;
};
export type Product = {
  id: string;
  name: string;
  category: string;
  riskText: string;
};
export type ProductPage = {
  items: Product[];
  page: number;
  pageSize: number;
  total: number;
  hasMore: boolean;
};
export type QuerySnapshot<T> = {
  data: T | null;
  loading: boolean;
  error: string | null;
};
export interface ResourceSubscription<T> {
  snapshot(): QuerySnapshot<T>;
  subscribe(listener: () => void): () => void;
  refresh(): void;
  release(): void;
}
export interface HomeRepository {
  content(): ResourceSubscription<HomeContent>;
  products(page: number): ResourceSubscription<ProductPage>;
}
export interface HomeClientState {
  selectedService(): string | null;
  selectService(id: string | null): void;
  subscribe(listener: () => void): () => void;
}
