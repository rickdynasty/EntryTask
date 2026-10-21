/** 页面边界回归：默认鉴权、公开帮助、取消不重弹，以及确认 Native 会话前不创建业务模型。 */
import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { AppNavigator } from '@app/navigation/AppNavigator';
import { ShellViewModel } from '@app/navigation/ShellViewModel';
import { requiresPageAuthentication } from '@app/navigation/routeAccess';
import type { Application } from '@app/CompositionRoot';
import type { Target } from '@features/services/public';

let mockScreen = 'Root';
let mockTarget: Target = {
  kind: 'accounts',
  title: '账户',
  requiresAuth: false,
};
const mockNavigation = {
  navigate: jest.fn(),
  push: jest.fn(),
  goBack: jest.fn(),
  isReady: () => true,
  canGoBack: () => false,
  resetRoot: jest.fn(),
  getRootState: () => ({
    routes:
      mockScreen === 'Root'
        ? [{ name: 'Root' }]
        : [
            { name: 'Root' },
            { name: 'Detail', params: { target: mockTarget } },
          ],
  }),
  getState: () => ({ routes: [{ name: 'Root' }] }),
};
jest.mock('@react-navigation/native', () => ({
  NavigationContainer: ({ children }: { children: React.ReactNode }) =>
    children,
  createNavigationContainerRef: () => mockNavigation,
}));
jest.mock('@react-navigation/native-stack', () => ({
  createNativeStackNavigator: () => ({
    Navigator: ({ children }: { children: React.ReactNode }) => children,
    Screen: ({
      name,
      component: Component,
    }: {
      name: string;
      component: React.ComponentType<{
        navigation: typeof mockNavigation;
        route: { key: string; params: { target: Target } };
      }>;
    }) =>
      name === mockScreen
        ? require('react').createElement(Component, {
            navigation: mockNavigation,
            route: { key: 'detail-1', params: { target: mockTarget } },
          })
        : null,
  }),
}));
jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('@shared/ui/Icon', () => ({ Icon: () => null }));
jest.mock('@features/services/public', () => ({
  ...jest.requireActual('@features/services/public'),
  DocumentScreen: () => null,
}));

function setup() {
  const command = jest.fn().mockResolvedValue(undefined);
  const shell = new ShellViewModel(
    {
      command,
      observeShell: () => () => {},
      readVisibility: async () => false,
      writeVisibility: async () => {},
    },
    { log: jest.fn() },
    jest.fn(),
  );
  // 让被放行页面使用可观察的轻量替身，避免测试请求真实 Native 模块。
  const createChannels = jest.fn(() => {
    throw new Error('protected root mounted');
  });
  const createDocument = jest.fn(() => ({ dispose: jest.fn() }));
  const application = {
    shell,
    logger: { log: jest.fn() },
    createChannels,
    createDocument,
  } as unknown as Application;
  return { application, shell, command, createChannels, createDocument };
}
let root: Renderer.ReactTestRenderer | undefined;
beforeEach(() => {
  mockScreen = 'Root';
  jest.clearAllMocks();
});
afterEach(async () => {
  await act(async () => root?.unmount());
  root = undefined;
});

test('unregistered pages are protected regardless of a false target hint', () => {
  for (const kind of [
    'Root',
    'accounts',
    'settings',
    'demo',
    'search',
    'futurePage',
  ]) {
    expect(requiresPageAuthentication(kind)).toBe(true);
  }
  expect(requiresPageAuthentication('helpCenter')).toBe(false);
});

test('root after Native cancellation stays gated without automatically reopening login', async () => {
  const { application, command, createChannels } = setup();
  await act(async () => {
    root = Renderer.create(<AppNavigator application={application} />);
  });
  expect(createChannels).not.toHaveBeenCalled();
  expect(command).not.toHaveBeenCalledWith({ type: 'authenticate' });
  expect(JSON.stringify(root!.toJSON())).toContain('帮助中心');
});

test('protected restored detail requests Bridge once and never constructs its data model as guest', async () => {
  mockScreen = 'Detail';
  mockTarget = { kind: 'accounts', title: '账户', requiresAuth: false };
  const { application, shell, command, createDocument } = setup();
  await act(async () => {
    root = Renderer.create(<AppNavigator application={application} />);
  });
  expect(
    command.mock.calls.filter(([value]) => value.type === 'authenticate'),
  ).toHaveLength(1);
  // 模拟取消后宿主只发布新 revision；不能因为事件刷新又弹一次。
  await act(async () => {
    shell.accept({
      tabId: 1,
      depth: 0,
      authenticated: false,
      sessionEpoch: 0,
      revision: 1,
    });
  });
  expect(
    command.mock.calls.filter(([value]) => value.type === 'authenticate'),
  ).toHaveLength(1);
  expect(createDocument).not.toHaveBeenCalled();
});

test('public help center renders as guest without any login or business request', async () => {
  mockScreen = 'Detail';
  mockTarget = { kind: 'helpCenter', title: '帮助中心', requiresAuth: false };
  const { application, command, createChannels, createDocument } = setup();
  await act(async () => {
    root = Renderer.create(<AppNavigator application={application} />);
  });
  expect(JSON.stringify(root!.toJSON())).toContain('欢迎使用 EntryTask');
  expect(command).not.toHaveBeenCalledWith({ type: 'authenticate' });
  expect(createDocument).not.toHaveBeenCalled();
  expect(createChannels).not.toHaveBeenCalled();
});

test('Bridge acknowledgment does not unlock a page; Native session confirmation unlocks the original target', async () => {
  mockScreen = 'Detail';
  mockTarget = { kind: 'accounts', title: '账户', requiresAuth: false };
  const { application, shell, createDocument } = setup();
  await act(async () => {
    root = Renderer.create(<AppNavigator application={application} />);
  });
  expect(createDocument).not.toHaveBeenCalled();
  await act(async () => {
    shell.accept({
      tabId: 1,
      depth: 0,
      authenticated: true,
      sessionEpoch: 1,
      revision: 1,
    });
  });
  expect(createDocument).toHaveBeenCalledWith(mockTarget, 1);
});

test('session expiry on public help neither requests login nor creates private models', async () => {
  mockScreen = 'Detail';
  mockTarget = { kind: 'helpCenter', title: '帮助中心', requiresAuth: false };
  const { application, shell, command, createChannels, createDocument } =
    setup();
  shell.accept({
    tabId: 1,
    depth: 1,
    authenticated: true,
    sessionEpoch: 1,
    revision: 1,
  });
  await act(async () => {
    root = Renderer.create(<AppNavigator application={application} />);
  });
  await act(async () => {
    shell.accept({
      tabId: 1,
      depth: 0,
      authenticated: false,
      sessionEpoch: 2,
      revision: 2,
    });
  });
  expect(JSON.stringify(root!.toJSON())).toContain('欢迎使用 EntryTask');
  expect(command).not.toHaveBeenCalledWith({ type: 'authenticate' });
  expect(createChannels).not.toHaveBeenCalled();
  expect(createDocument).not.toHaveBeenCalled();
});

test('session expiry unmounts protected detail and cancels its model before any new login', async () => {
  mockScreen = 'Detail';
  mockTarget = { kind: 'accounts', title: '账户', requiresAuth: false };
  const { application, shell, command, createDocument } = setup();
  shell.accept({
    tabId: 1,
    depth: 1,
    authenticated: true,
    sessionEpoch: 1,
    revision: 1,
  });
  await act(async () => {
    root = Renderer.create(<AppNavigator application={application} />);
  });
  const model = createDocument.mock.results[0]!.value;
  await act(async () => {
    shell.accept({
      tabId: 1,
      depth: 0,
      authenticated: false,
      sessionEpoch: 2,
      revision: 2,
    });
  });
  expect(model.dispose).toHaveBeenCalledTimes(1);
  expect(command).toHaveBeenCalledWith({ type: 'authenticate' });
  expect(createDocument).toHaveBeenCalledTimes(1);
  expect(JSON.stringify(root!.toJSON())).toContain('登录后继续');
});
