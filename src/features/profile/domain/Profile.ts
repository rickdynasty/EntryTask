/** EntryTask RN：我的业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
import type { Service, Money } from '@features/services/public';
export type Marketing = {
  services: Service[];
  assetSlogan: string;
  assetNotice: string;
  creditTitle: string;
  creditSubtitle: string;
  loanTitle: string;
  loanLimit: Money;
  loanRate: string;
  isDemo: boolean;
};
