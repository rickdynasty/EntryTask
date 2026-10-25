/** EntryTask RN：会员业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
import type { ContentItem, Service } from '@features/services/public';
export type MemberContent = {
  title: string;
  welcome: string;
  welcomeSubtitle: string;
  benefits: ContentItem[];
  benefitEntries: Service[];
  checkinTitle: string;
  cardTitle: string;
  cardSubtitle: string;
  isDemo: boolean;
};
