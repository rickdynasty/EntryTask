/** EntryTask RN：我的业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
import type { Money } from '@features/services/public';
/** 财务概览是受保护接口的展示契约；营销额度不代表授信。 */
export type FinancialOverview = {
  month: string;
  monthlyIncome: Money;
  monthlyExpense: Money;
  creditBill: {
    hasCard: boolean;
    unpaid: Money | null;
    dueDate: string | null;
  };
  loanOffer: { marketingOnly: true; limit: Money; annualRate: string };
  isDemo: true;
};
