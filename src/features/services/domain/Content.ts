/** EntryTask RN：通用服务与详情业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
export type TargetKind =
  | 'products'
  | 'product'
  | 'assets'
  | 'account'
  | 'accounts'
  | 'transaction'
  | 'transactions'
  | 'article'
  | 'articles'
  | 'activity'
  | 'activities'
  | 'benefit'
  | 'benefits'
  | 'claim'
  | 'claims'
  | 'energy'
  | 'checkin'
  | 'profile'
  | 'item'
  | 'items'
  | 'services'
  | 'search'
  | 'messages'
  | 'message'
  | 'settings'
  | 'demo'
  | 'helpCenter';
export type Target = {
  kind: TargetKind;
  title: string;
  /** API 目标的历史提示；页面放行由 app 导航集中策略决定，false 不能绕过门禁。 */
  requiresAuth: boolean;
  id?: string;
  category?: string;
};
export type Service = {
  id: string;
  title: string;
  icon: string;
  badge?: string;
  target: Target;
};
export type Category = { id: string; title: string };
export type Campaign = {
  id: string;
  title: string;
  subtitle: string;
  assetKey: string;
  tone: string;
  target: Target;
};
export type ContentItem = {
  id: string;
  title: string;
  summary?: string;
  subtitle?: string;
  date?: string;
  category?: string;
  paragraphs?: string[];
  assetKey?: string;
  status?: string;
  tone?: string;
  isRead?: boolean;
  target?: Target;
  amountMinor?: string;
  direction?: string;
  numberMasked?: string;
  balance?: Money;
  requiredLevel?: number;
  stock?: number;
};
export type Money = {
  currency: string;
  minorUnit: number;
  amountMinor: string;
};
export type Asset = {
  total: Money;
  liabilities: Money;
  net: Money;
  income: Money;
  asOf: string;
  categories: { title: string; amountMinor: string }[];
};
export type Page<T> = {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  hasMore: boolean;
};
export type Identity = {
  nickname: string;
  phoneMasked: string;
  level: number;
  bankCards: number;
  todos: number;
  coupons: number;
  points: number;
  energy: number;
  isDemo: boolean;
};
export type Checkin = {
  businessDate: string;
  checkedToday: boolean;
  consecutiveDays: number;
  energy: number;
  awardedEnergy: number;
  alreadyChecked: boolean;
  rewardDays: number[];
};
