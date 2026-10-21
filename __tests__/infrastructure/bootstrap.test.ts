/** EntryTask RN 测试：覆盖启动流程日志、Native Telemetry 连接时序和错误隔离。 */
import { createApplication } from '@app/CompositionRoot';
import { nativeGateway } from '@platform/native/NativeGateway';
import { AppError } from '@shared/domain/AppError';

jest.mock('@platform/native/NativeGateway', () => ({
  nativeGateway: {
    observeShell: jest.fn(() => () => {}),
    command: jest.fn().mockResolvedValue(undefined),
    bootstrap: jest.fn(),
    session: jest.fn().mockResolvedValue({ authenticated: false, sessionEpoch: 0 }),
    readVisibility: jest.fn().mockResolvedValue(false),
    writeVisibility: jest.fn().mockResolvedValue(undefined),
    emitLogs: jest.fn().mockResolvedValue(undefined),
  },
}));

test('host failure can be logged through Native telemetry before handshake success', async () => {
  const error = jest.spyOn(console, 'error').mockImplementation(() => {});
  const info = jest.spyOn(console, 'info').mockImplementation(() => {});
  jest
    .mocked(nativeGateway.bootstrap)
    .mockRejectedValue(new AppError('HOST_RESOURCE_MISSING', '缺少资源'));
  const app = createApplication();
  await expect(app.bootstrap('flow-1')).rejects.toMatchObject({
    code: 'HOST_RESOURCE_MISSING',
  });
  app.logger.log('error', 'bootstrap', 'bootstrap.failed', {
    code: 'HOST_RESOURCE_MISSING',
    flowId: 'flow-1',
  });
  await app.logger.flush();
  expect(nativeGateway.emitLogs).toHaveBeenCalledWith(
    expect.arrayContaining([
      expect.objectContaining({
        event: 'bootstrap.failed',
        code: 'HOST_RESOURCE_MISSING',
        flowId: 'flow-1',
      }),
    ]),
  );
  app.dispose();
  error.mockRestore();
  info.mockRestore();
});

test('startup session bridge is authoritative for the RN shell login gate', async () => {
  const info = jest.spyOn(console, 'info').mockImplementation(() => {});
  jest.mocked(nativeGateway.bootstrap).mockResolvedValue({
    protocolVersion: '1.1',
    bootId: 'boot-1',
    componentName: 'EntryTask',
    capabilities: [],
    shell: {
      tabId: 1,
      depth: 0,
      authenticated: false,
      sessionEpoch: 0,
      revision: 1,
    },
  });
  jest
    .mocked(nativeGateway.session)
    .mockResolvedValue({ authenticated: true, sessionEpoch: 4 });
  const app = createApplication();
  await app.bootstrap('flow-session');
  expect(nativeGateway.session).toHaveBeenCalledTimes(1);
  expect(app.shell.getSnapshot()).toMatchObject({
    authenticated: true,
    sessionEpoch: 4,
  });
  app.dispose();
  info.mockRestore();
});
