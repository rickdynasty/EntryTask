/** EntryTask RN：共享呈现状态适配：连接资源订阅与 React 生命周期。 */
import { useEffect, useSyncExternalStore } from 'react';
import type { ResourceViewModel } from './ResourceViewModel';
export function useResource<T>(model: ResourceViewModel<T>, enabled = true) {
  // React 负责订阅稳定快照，effect 按页面可见性管理资源激活与释放。
  const state = useSyncExternalStore(model.subscribe, model.getSnapshot);
  useEffect(() => {
    if (enabled) {
      model.activate();
    }
    return model.deactivate;
  }, [model, enabled]);
  return state;
}
