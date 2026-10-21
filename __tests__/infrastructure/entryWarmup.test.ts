/** 引擎加载入口只注册组件；预热阶段不能提前创建业务图或执行 Native 握手/请求。 */
import { AppRegistry } from 'react-native';
import * as composition from '@app/CompositionRoot';
import { nativeGateway } from '@platform/native/NativeGateway';

jest.mock('@app/navigation/AppNavigator', () => ({ AppNavigator: () => null }));
jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('@platform/native/NativeGateway', () => ({
  nativeGateway: {
    bootstrap: jest.fn(),
    session: jest.fn(),
    emitLogs: jest.fn(),
    observeShell: jest.fn(),
    command: jest.fn(),
  },
}));

test('bundle evaluation registers EntryTask without mounting its business graph', () => {
  const createApplication = jest.spyOn(composition, 'createApplication');
  const register = jest.spyOn(AppRegistry, 'registerComponent');
  require('../../src/index');
  expect(register).toHaveBeenCalledWith('EntryTask', expect.any(Function));
  expect(createApplication).not.toHaveBeenCalled();
  expect(nativeGateway.bootstrap).not.toHaveBeenCalled();
  expect(nativeGateway.session).not.toHaveBeenCalled();
  expect(nativeGateway.emitLogs).not.toHaveBeenCalled();
  register.mockRestore();
  createApplication.mockRestore();
});
