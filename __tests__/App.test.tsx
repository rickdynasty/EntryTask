import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { Button } from 'react-native';
import App from '../src/app/App';

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

jest.mock('@platform/native/NativeGateway', () => ({
  nativeGateway: {
    bootstrap: jest.fn().mockRejectedValue(new Error('not linked')),
    emitLogs: jest.fn(),
  },
}));

test('shows a retryable native capability error instead of switching HTTP routes', async () => {
  const consoleSpy = jest.spyOn(console, 'info').mockImplementation(() => {});
  let root: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    root = ReactTestRenderer.create(<App />);
  });
  expect(root!.root.findByType(Button).props.title).toBe('重试初始化');
  await ReactTestRenderer.act(async () => root!.unmount());
  consoleSpy.mockRestore();
});
