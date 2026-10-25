/** EntryTask RN：应用组装根：创建日志、网络、查询缓存、Shell 和各业务域 ViewModel。 */
import { Logger } from '@infra/logging/Logger';
import { defaultEndpointRegistry } from '@infra/network/EndpointRegistry';
import { TransportRouter } from '@infra/network/TransportRouter';
import { NativeTransport } from '@infra/network/transports/NativeTransport';
import { RnHttpTransport } from '@infra/network/transports/RnHttpTransport';
import { createAppStore } from '@infra/state/createAppStore';
import { createResourceClient } from '@infra/state/ResourceClient';
import { createHomePageViewModel } from '@features/home/public';
import { createWealthViewModel } from '@features/wealth/public';
import { createMemberViewModel } from '@features/member/public';
import { createProfileViewModel } from '@features/profile/public';
import { createMessaging } from '@features/messaging/public';
import { nativeGateway } from '@platform/native/NativeGateway';
import { ShellViewModel } from './navigation/ShellViewModel';
import { createDocumentModel } from './navigation/createDocumentModel';
import type { Target } from '@features/services/public';

export function createApplication() {
  // 先构造有界 RN 日志器，再连接 Native sink，确保启动期也能输出诊断。
  const logger = new Logger(__DEV__);
  logger.setSink(events => nativeGateway.emitLogs(events));
  const nativeTransport = new NativeTransport();
  const network = new TransportRouter(
    defaultEndpointRegistry,
    {
      nativeMock: nativeTransport,
      native: nativeTransport,
      rnHttp: new RnHttpTransport(),
    },
    logger,
  );
  const runtime = createAppStore(network);
  const client = createResourceClient(runtime);
  const shell = new ShellViewModel(nativeGateway, logger, () => {
    client.reset();
    runtime.resetPages();
  });
  return {
    logger,
    shell,
    createChannels(epoch: number) {
      const pageStates = {
        1: runtime.pageState(epoch, 1),
        2: runtime.pageState(epoch, 2),
        3: runtime.pageState(epoch, 3),
        4: runtime.pageState(epoch, 4),
      };
      // 同一会话共用一份客户端资源缓存，各业务域按需订阅和释放。
      const home = createHomePageViewModel(client, epoch, pageStates[1]),
        wealth = createWealthViewModel(client, epoch, pageStates[2]),
        member = createMemberViewModel(client, epoch),
        profile = createProfileViewModel(client, epoch),
        messaging = createMessaging(client, epoch, logger);
      return {
        pageStates,
        home,
        wealth,
        member,
        profile,
        messaging,
        dispose() {
          home.dispose();
          wealth.dispose();
          member.dispose();
          profile.dispose();
          messaging.dispose();
        },
      };
    },
    createMessaging: (epoch: number) => createMessaging(client, epoch, logger),
    createDocument: (target: Target, epoch: number) =>
      createDocumentModel(client, target, epoch, logger),
    async bootstrap(flowId: string) {
      logger.log('info', 'bootstrap', 'bootstrap.started', { flowId });
      shell.start();
      const snapshot = await nativeGateway.bootstrap(flowId);
      if (snapshot?.shell) shell.accept(snapshot.shell);
      // 通过只读 Session Bridge 校准认证状态；RN 不从本地缓存推断登录事实。
      const session = await nativeGateway.session();
      shell.acceptSession(session);
      logger.log('info', 'session', 'session.snapshot.checked', {
        pageId: shell.getSnapshot().authenticated ? 'authenticated' : 'guest',
        flowId,
      });
      logger.log('info', 'bootstrap', 'bootstrap.completed', { flowId });
    },
    dispose() {
      // 页面、查询 Store 与日志缓冲按应用生命周期统一释放。
      shell.dispose();
      runtime.dispose();
      logger.dispose();
    },
  };
}
export type Application = ReturnType<typeof createApplication>;
