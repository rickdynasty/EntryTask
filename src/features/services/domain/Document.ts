/** EntryTask RN：通用服务与详情业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
import type { Target } from './Content';
export type DocumentRow = {
  id: string;
  title: string;
  subtitle: string;
  value: string;
  assetKey?: string;
  target?: Target;
  read?: boolean;
};
export type Document = {
  title: string;
  subtitle: string;
  paragraphs: string[];
  metrics: { label: string; value: string }[];
  rows: DocumentRow[];
  hasMore: boolean;
  page: number;
  businessDate?: string;
  checkedToday?: boolean;
  actionLabel?: string;
  actionDisabled?: boolean;
};
