/** EntryTask RN 测试：覆盖应用启动加载、初始化失败提示和重试交互。 */
import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { nativeGateway } from '@platform/native/NativeGateway';
import { AppError } from '@shared/domain/AppError';
import { Button } from 'react-native';
import App from '../src/app/App';

jest.mock('../src/app/navigation/AppNavigator', () => ({
  AppNavigator: () => null,
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

jest.mock('@platform/native/NativeGateway', () => ({
  nativeGateway: {
    observeShell: jest.fn(() => () => {}),
    command: jest.fn().mockResolvedValue(undefined),
    bootstrap: jest.fn().mockRejectedValue(new Error('not linked')),
    session: jest.fn().mockResolvedValue({ authenticated: false, sessionEpoch: 0 }),
    emitLogs: jest.fn(),
  },
}));

test('shows a retryable native capability error instead of switching HTTP routes', async () => {
  const consoleSpy = jest.spyOn(console, 'info').mockImplementation(() => {});
  const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
  jest
    .mocked(nativeGateway.bootstrap)
    .mockRejectedValue(
      new AppError('HOST_RESOURCE_MISSING', '应用缺少运行资源'),
    );
  let root: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    root = ReactTestRenderer.create(<App />);
  });
  expect(root!.root.findByType(Button).props.title).toBe('重试初始化');
  expect(JSON.stringify(root!.toJSON())).toContain('HOST_RESOURCE_MISSING');
  const firstFlow = jest
    .mocked(nativeGateway.bootstrap)
    .mock.calls.slice(-1)[0]![0];
  await ReactTestRenderer.act(async () => {
    root!.root.findByType(Button).props.onPress();
  });
  const secondFlow = jest
    .mocked(nativeGateway.bootstrap)
    .mock.calls.slice(-1)[0]![0];
  expect(secondFlow).not.toEqual(firstFlow);
  expect(errorSpy).toHaveBeenCalledWith(
    '[EntryTask:RN]',
    expect.stringContaining('HOST_RESOURCE_MISSING'),
  );
  await ReactTestRenderer.act(async () => root!.unmount());
  errorSpy.mockRestore();
  consoleSpy.mockRestore();
});
