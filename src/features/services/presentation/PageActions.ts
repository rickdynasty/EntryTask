/** EntryTask RN：通用服务与详情业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import type { Target } from '../public';
import type { HeaderActions } from '@shared/ui/ChannelHeader';
export type PageActions = {
  /** 记录不含业务载荷的交互事件，供 RN 与 Native 日志关联排查。 */
  track: (module: string, event: string, pageId: string) => void;
  open: (target: Target) => void;
  switchTab: (id: number) => void;
  login: () => void;
  toggleVisibility: () => void;
  header: HeaderActions;
};
export type PageSession = { authenticated: boolean; assetsVisible: boolean };
