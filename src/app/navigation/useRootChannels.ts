/** 根页模型组装属于提交后的副作用；渲染只读取匹配当前会话的绑定。 */
import { useEffect, useState } from 'react';
import type { Application } from '../CompositionRoot';

type Channels = ReturnType<Application['createChannels']>;
export function useRootChannels(
  application: Pick<Application, 'createChannels'>,
  epoch: number,
): Channels | null {
  const [binding, setBinding] = useState<{
    epoch: number;
    channels: Channels;
  } | null>(null);
  useEffect(() => {
    const channels = application.createChannels(epoch);
    setBinding({ epoch, channels });
    return () => channels.dispose();
  }, [application, epoch]);
  // 新会话依赖未提交前，旧VM必须先从UI卸载，不能展示旧账号数据。
  return binding?.epoch === epoch ? binding.channels : null;
}
