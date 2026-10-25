/** EntryTask RN：RN 导航组装：同步 Native 根 Tab、处理认证门禁并承载通用详情页。 */
import { SafeAreaView } from 'react-native-safe-area-context';
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import {
  NavigationContainer,
  createNavigationContainerRef,
} from '@react-navigation/native';
import {
  createNativeStackNavigator,
  type NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { HomeRoute } from '@features/home/public';
import { WealthRoute } from '@features/wealth/public';
import { MemberRoute } from '@features/member/public';
import { ProfileRoute } from '@features/profile/public';
import type { PageActions, Target } from '@features/services/public';
import { DocumentScreen } from '@features/services/public';
import { createId } from '@infra/logging/Logger';
import { useResource } from '@shared/presentation/useResource';
import { DemoActionSheet } from '@shared/ui/DashboardComponents';
import { Icon } from '@shared/ui/Icon';
import {
  DemoFootnote,
  PrimaryButton,
  Surface,
  ui,
} from '@shared/ui/Primitives';
import type { Application } from '../CompositionRoot';
import { AuthenticationGate } from './AuthenticationGate';
import { helpCenterTarget, requiresPageAuthentication } from './routeAccess';
type StackParams = { Root: undefined; Detail: { target: Target } };
const Stack = createNativeStackNavigator<StackParams>();
const Context = createContext<Application | null>(null);
function useApplication() {
  const value = useContext(Context);
  if (!value) throw new Error('Missing application');
  return value;
}
const titles = ['首页', '财富', '会员', '我的'];
export function AppNavigator({ application }: { application: Application }) {
  // 导航容器只创建一次，Native Shell 负责提供根 Tab 与会话快照。
  const navigation = useMemo(
    () => createNavigationContainerRef<StackParams>(),
    [],
  );
  const shell = useSyncExternalStore(
    application.shell.subscribe,
    application.shell.getSnapshot,
  );
  const lastEpoch = useRef(shell.sessionEpoch);
  const previouslyAuthenticated = useRef(shell.authenticated);
  useEffect(() => {
    if (lastEpoch.current !== shell.sessionEpoch) {
      // 已认证会话切换时清空私有页面，游客首次登录则保留认证前目标。
      lastEpoch.current = shell.sessionEpoch;
      const current = navigation.getRootState()?.routes.at(-1);
      const target =
        current?.params && 'target' in current.params
          ? current.params.target
          : undefined;
      const publicPage =
        current?.name === 'Detail' &&
        typeof target === 'object' &&
        target !== null &&
        'kind' in target &&
        target.kind === 'helpCenter';
      if (previouslyAuthenticated.current) {
        if (navigation.isReady() && navigation.canGoBack()) {
          // 清掉旧会话私有路由；公开帮助页保留展示，但不保留下面的私有页面参数。
          navigation.resetRoot(
            publicPage
              ? {
                  index: 1,
                  routes: [
                    { name: 'Root' },
                    { name: 'Detail', params: { target: helpCenterTarget } },
                  ],
                }
              : { index: 0, routes: [{ name: 'Root' }] },
          );
        }
        if (!shell.authenticated && !publicPage) {
          // 已登录页面失去会话只触发一次认证；首次启动取消不经过这个分支。
          application.logger.log('info', 'session', 'authentication.expired', {
            pageId: 'root-shell',
          });
          application.shell.command({ type: 'authenticate' });
        }
      }
    }
    previouslyAuthenticated.current = shell.authenticated;
  }, [application, shell.sessionEpoch, shell.authenticated, navigation]);
  useEffect(() => {
    // Native 返回协调器发现深度不一致时会重发快照，以 RN 导航栈纠正投影，避免误退出。
    if (!navigation.isReady()) return;
    const depth = Math.max(
      0,
      (navigation.getRootState()?.routes.length ?? 1) - 1,
    );
    if (shell.depth !== depth) {
      application.logger.log('warn', 'navigation', 'depth.resynchronized', {
        pageId: 'root-shell',
      });
      application.shell.command({ type: 'navigation', depth });
    }
  }, [
    application,
    navigation,
    shell.authenticated,
    shell.depth,
    shell.revision,
  ]);
  return (
    <Context.Provider value={application}>
      <NavigationContainer
        ref={navigation}
        onReady={() =>
          application.shell.command({ type: 'navigation', depth: 0 })
        }
        onStateChange={state =>
          application.shell.command({
            type: 'navigation',
            depth: Math.max(0, (state?.routes.length ?? 1) - 1),
          })
        }
      >
        <Stack.Navigator
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: '#F5F5F5' },
            animation: 'slide_from_right',
          }}
        >
          <Stack.Screen name="Root" component={RootRoute} />
          <Stack.Screen name="Detail" component={DetailRoute} />
        </Stack.Navigator>
      </NavigationContainer>
    </Context.Provider>
  );
}

/** 根页挂载前检查会话；Native 首次认证取消后不再自动请求。 */
function RootRoute({
  navigation,
}: NativeStackScreenProps<StackParams, 'Root'>) {
  const application = useApplication();
  const shell = useSyncExternalStore(
    application.shell.subscribe,
    application.shell.getSnapshot,
  );
  if (!shell.authenticated)
    return (
      <AuthenticationGate
        application={application}
        pageId="root-shell"
        onHelp={() =>
          navigation.navigate('Detail', { target: helpCenterTarget })
        }
      />
    );
  return <RootHub />;
}

function RootHub() {
  const application = useApplication();
  const shell = useSyncExternalStore(
    application.shell.subscribe,
    application.shell.getSnapshot,
  );
  const [demoTitle, setDemoTitle] = useState<string | null>(null);
  const channels = useMemo(
    () => application.createChannels(shell.sessionEpoch),
    [application, shell.sessionEpoch],
  );
  useEffect(() => () => channels.dispose(), [channels]);
  const unread = useResource(channels.messaging.unread, shell.authenticated);
  const open = useCallback(
    (target: Target) => {
      application.logger.log('info', 'navigation', 'target.opened', {
        pageId: target.kind,
      });
      setDemoTitle(target.title);
    },
    [application],
  );
  const demo = (title: string): Target => ({
    kind: 'demo',
    title,
    requiresAuth: false,
  });
  const actions: PageActions = {
    track: (module, event, pageId) =>
      application.logger.log('info', module, event, { pageId }),
    open,
    switchTab: tabId => application.shell.command({ type: 'selectTab', tabId }),
    login: () => application.shell.command({ type: 'authenticate' }),
    toggleVisibility: application.shell.toggleVisibility,
    header: {
      search: () =>
        open({ kind: 'search', title: '搜索', requiresAuth: false }),
      messages: () =>
        open({ kind: 'messages', title: '消息中心', requiresAuth: true }),
      advisor: () => open(demo('在线客服')),
      scan: () => open(demo('扫一扫')),
      settings: () =>
        open({ kind: 'settings', title: '设置', requiresAuth: false }),
      login: () => application.shell.command({ type: 'authenticate' }),
    },
  };
  useEffect(() => {
    application.logger.log('info', 'navigation', 'tab.entered', {
      pageId: titles[shell.tabId - 1] ?? '首页',
    });
  }, [application, shell.tabId]);
  const common = {
    actions,
    session: shell,
    unread: unread.data?.unreadCount,
  };
  return (
    <View style={styles.fill}>
      {shell.tabId === 1 && (
        <HomeRoute
          model={channels.home}
          notificationError={unread.error}
          onRetryNotifications={channels.messaging.unread.refresh}
          active={shell.depth === 0 && demoTitle === null}
          pageState={channels.pageStates[1]}
          {...common}
        />
      )}
      {shell.tabId === 2 && (
        <WealthRoute
          model={channels.wealth}
          active={shell.depth === 0 && demoTitle === null}
          pageState={channels.pageStates[2]}
          {...common}
        />
      )}
      {shell.tabId === 3 && (
        <MemberRoute
          model={channels.member}
          active={shell.depth === 0 && demoTitle === null}
          pageState={channels.pageStates[3]}
          {...common}
        />
      )}
      {shell.tabId === 4 && (
        <ProfileRoute
          model={channels.profile}
          active={shell.depth === 0 && demoTitle === null}
          pageState={channels.pageStates[4]}
          {...common}
        />
      )}
      <DemoActionSheet title={demoTitle} onClose={() => setDemoTitle(null)} />
    </View>
  );
}

function PageHeader({ title, onBack }: { title: string; onBack: () => void }) {
  return (
    <View style={styles.header}>
      <Pressable accessibilityLabel="返回" hitSlop={12} onPress={onBack}>
        <Icon name="back" size={23} />
      </Pressable>
      <Text numberOfLines={1} style={styles.headerTitle}>
        {title}
      </Text>
      <View style={styles.headerSpacer} />
    </View>
  );
}
function DetailRoute({
  route,
  navigation,
}: NativeStackScreenProps<StackParams, 'Detail'>) {
  const app = useApplication();
  const shell = useSyncExternalStore(
    app.shell.subscribe,
    app.shell.getSnapshot,
  );
  const target = route.params.target;
  const onOpen = (next: Target) => {
    navigation.push('Detail', { target: next });
  };
  // 覆盖页面入口、嵌套跳转与恢复路由；鉴权前不创建 RemoteDetail 的 VM 或查询。
  if (requiresPageAuthentication(target.kind) && !shell.authenticated)
    return (
      <AuthenticationGate
        key={`${route.key}:${shell.sessionEpoch}`}
        application={app}
        pageId={route.key}
        autoRequest
        onBack={navigation.goBack}
        onHelp={() => navigation.push('Detail', { target: helpCenterTarget })}
      />
    );
  if (target.kind === 'helpCenter')
    return (
      <SafeAreaView edges={['bottom']} style={styles.fill}>
        <PageHeader title="帮助中心" onBack={navigation.goBack} />
        <ScrollView contentContainerStyle={styles.localContent}>
          <Surface>
            <Text style={ui.heading}>欢迎使用 EntryTask</Text>
            <Text style={ui.body}>
              首页、财富、会员、我的及业务详情需要登录后访问。
            </Text>
            <Text style={ui.body}>
              取消登录后可点击“前往登录”重试。账号不存在时，请先在原生登录页进入注册。
            </Text>
            <Text style={ui.muted}>
              本工程使用本地模拟数据，不连接真实银行服务。
            </Text>
          </Surface>
        </ScrollView>
      </SafeAreaView>
    );
  if (target.kind === 'demo' || target.kind === 'settings')
    return (
      <SafeAreaView edges={['bottom']} style={styles.fill}>
        <PageHeader title={target.title} onBack={navigation.goBack} />
        <LocalPage target={target} onBack={navigation.goBack} />
      </SafeAreaView>
    );
  return (
    <SafeAreaView edges={['bottom']} style={styles.fill}>
      <RemoteDetail
        target={target}
        routeKey={route.key}
        onOpen={onOpen}
        onBack={navigation.goBack}
      />
    </SafeAreaView>
  );
}
function RemoteDetail({
  target,
  routeKey,
  onOpen,
  onBack,
}: {
  target: Target;
  routeKey: string;
  onOpen: (target: Target) => void;
  onBack: () => void;
}) {
  const app = useApplication();
  const shell = useSyncExternalStore(
    app.shell.subscribe,
    app.shell.getSnapshot,
  );
  const model = useMemo(
    () => app.createDocument(target, shell.sessionEpoch),
    [app, target, shell.sessionEpoch],
  );
  useEffect(() => () => model.dispose(), [model]);
  useEffect(() => {
    app.logger.log('info', 'navigation', 'page.entered', {
      pageId: target.kind,
      flowId: routeKey,
    });
    const abort = new AbortController();
    if (target.kind === 'message' && target.id) {
      const messages = app.createMessaging(shell.sessionEpoch);
      app.logger.log('info', 'messages', 'message.read.started', {
        pageId: 'message',
      });
      void messages
        .read(target.id, createId(), abort.signal)
        .then(() =>
          app.logger.log('info', 'messages', 'message.read.completed', {
            pageId: 'message',
          }),
        )
        .catch(() =>
          app.logger.log('warn', 'messages', 'message.read.failed', {
            code: 'READ_FAILED',
            pageId: 'message',
          }),
        );
    }
    return () => abort.abort();
  }, [app, target, routeKey, shell.sessionEpoch]);
  return (
    <View style={styles.fill}>
      <PageHeader title={target.title} onBack={onBack} />
      <DocumentScreen
        model={model}
        target={target}
        onOpen={onOpen}
        authenticated={shell.authenticated}
        assetsVisible={shell.assetsVisible}
        onAuthenticate={() => app.shell.command({ type: 'authenticate' })}
        onToggleVisibility={app.shell.toggleVisibility}
      />
    </View>
  );
}
function LocalPage({ target, onBack }: { target: Target; onBack: () => void }) {
  const app = useApplication();
  const shell = useSyncExternalStore(
    app.shell.subscribe,
    app.shell.getSnapshot,
  );
  return (
    <ScrollView contentContainerStyle={styles.localContent}>
      <Surface>
        <Text style={ui.heading}>{target.title}</Text>
        {target.kind === 'settings' ? (
          <>
            <View style={ui.between}>
              <Text style={ui.body}>显示资产金额</Text>
              <Switch
                accessibilityLabel="显示资产金额"
                value={shell.assetsVisible}
                onValueChange={app.shell.toggleVisibility}
              />
            </View>
            <Text style={ui.muted}>
              资产显隐仅在当前会话生效；新会话默认隐藏。
            </Text>
            <Text style={ui.body}>关于 EntryTask</Text>
            <Text style={ui.muted}>
              本地银行业务原型 · 所有数据均为模拟数据
            </Text>
            {shell.authenticated && (
              <PrimaryButton
                title="退出登录"
                onPress={() =>
                  Alert.alert(
                    '退出登录',
                    '退出后将清除当前会话，保留本地模拟记录。',
                    [
                      { text: '取消', style: 'cancel' },
                      {
                        text: '退出',
                        style: 'destructive',
                        onPress: () => {
                          app.shell.command({ type: 'logout' });
                          onBack();
                        },
                      },
                    ],
                  )
                }
              />
            )}
          </>
        ) : (
          <>
            <Text style={ui.body}>此服务仅作界面与流程演示。</Text>
            <Text style={ui.body}>
              当前工程不连接真实银行后端，不提供真实开户、转账、支付、授信、申卡、相机扫描或人工客服，也不会收集身份核验资料。
            </Text>
            <PrimaryButton title="我知道了，返回" onPress={onBack} />
          </>
        )}
      </Surface>
      <DemoFootnote />
    </ScrollView>
  );
}
const styles = StyleSheet.create({
  localContent: { padding: 16, gap: 16 },
  headerSpacer: { width: 23 },
  fill: { flex: 1 },
  hidden: { display: 'none' },
  header: {
    height: 52,
    backgroundColor: '#FFF',
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: '#EDE8E1',
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 17,
    fontWeight: '600',
    color: '#242321',
    marginHorizontal: 10,
  },
});
