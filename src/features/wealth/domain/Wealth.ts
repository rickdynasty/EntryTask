/** EntryTask RN：财富业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
import type {
  Service,
  Category,
  Campaign,
  ContentItem,
} from '@features/services/public';
export type Product = {
  id: string;
  name: string;
  category: string;
  riskText: string;
  ratio: string;
  indicatorLabel: string;
  period: string;
  term: string;
  minimum: string;
  riskLevel: string;
  distribution: string;
  description: string[];
};
export type WealthContent = {
  title: string;
  services: Service[];
  categories: Category[];
  market: ContentItem;
  banner: Campaign;
  isDemo: boolean;
};
