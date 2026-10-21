/** 页面访问策略由导航层统一维护，接口返回的 requiresAuth 不能把页面降为公开。 */
import type { Target } from '@features/services/public';

// 未登记页面默认受保护；公开页不能构建个人资源模型或调用受保护能力。
const publicPages: ReadonlySet<string> = new Set(['helpCenter']);
export function requiresPageAuthentication(kind: string): boolean {
  return !publicPages.has(kind);
}
export const helpCenterTarget: Target = {
  kind: 'helpCenter',
  title: '帮助中心',
  requiresAuth: false,
};
