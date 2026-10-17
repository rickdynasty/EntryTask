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
  logger.dispose();
});
