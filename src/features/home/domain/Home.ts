/** EntryTask RN：首页业务域模块：负责业务域数据契约，不跨域持有页面私有实现。 */
import type { Service, Category, Campaign } from '@features/services/public';
export type HomePage = {
  title: string;
  subtitle: string;
  services: Service[];
  quickServices: Service[];
  shortcuts: Service[];
  banners: Campaign[];
  categories: Category[];
  searchPlaceholder: string;
  isDemo: boolean;
  sections: HomeSection[];
};

/** 首页运营分区由内容接口提供；样式枚举限制服务端可配置范围。 */
export type HomeSection = {
  id: string;
  title: string;
  subtitle: string;
  style: 'banner' | 'cards' | 'menu' | 'news';
  entries: (Service & { subtitle: string; assetKey: string; tone: string })[];
};
