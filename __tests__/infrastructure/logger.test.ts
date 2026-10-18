/** EntryTask RN 测试：覆盖白名单脱敏、有界缓冲、批量投递和失败保护。 */
import { Logger } from '@infra/logging/Logger';
import type { LogFields } from '@platform/ports/LoggerPort';

test('RN logging works without bridge and strips non-allowlisted data', () => {
  const logger = new Logger(false);
  logger.log('info', 'test', 'started', {
    requestId: 'request-1',
    password: 'secret',
    token: 'credential',
  } as LogFields);
  const output = JSON.stringify(logger.snapshot());
  expect(output).toContain('request-1');
  expect(output).not.toContain('secret');
  expect(output).not.toContain('credential');
  logger.dispose();
});

test('bounded logging sink failures do not throw or recursively log', async () => {
  const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
  const logger = new Logger(false);
  logger.setSink(async () => {
    throw new Error('bridge missing');
  });
  for (let i = 0; i < 250; i++) {
    logger.log('info', 'test', 'event');
  }
  expect(logger.snapshot()).toHaveLength(200);
  await logger.flush();
  expect(logger.droppedCount).toBeGreaterThan(0);
  await logger.flush();
  expect(warn).toHaveBeenCalledTimes(1);
  logger.dispose();
  warn.mockRestore();
});

test('errors and warnings retain severity even without the native sink', () => {
  const error = jest.spyOn(console, 'error').mockImplementation(() => {});
  const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
  const logger = new Logger(false);
  logger.log('error', 'bootstrap', 'bootstrap.failed', {
    code: 'HOST_RESOURCE_MISSING',
  });
  logger.log('warn', 'bootstrap', 'retry');
  expect(error).toHaveBeenCalledWith(
    '[EntryTask:RN]',
    expect.stringContaining('HOST_RESOURCE_MISSING'),
  );
  expect(warn).toHaveBeenCalledTimes(1);
  logger.dispose();
  error.mockRestore();
  warn.mockRestore();
});
