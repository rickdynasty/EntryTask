/** EntryTask RN 测试：覆盖 RTK Query 资源共享、释放、写后失效和会话缓存隔离。 */
import { createAppStore } from '@infra/state/createAppStore';
import { createResourceClient } from '@infra/state/ResourceClient';

test('query consumers share a request; only the final release cancels it', async () => {
  jest.useFakeTimers();
  let complete: (value: unknown) => void = () => {};
  let signal: AbortSignal | undefined;
  const execute = jest.fn((_request, requestSignal: AbortSignal) => {
    signal = requestSignal;
    return new Promise(resolve => {
      complete = resolve;
    });
  });
  const runtime = createAppStore({ execute });
  const client = createResourceClient(runtime);
  const source = client.source<{ title: string }>(
    { apiId: 'home.content' },
    () => true,
  );
  const first = source.open();
  const second = source.open();
  expect(execute).toHaveBeenCalledTimes(1);
  first.release();
  expect(signal?.aborted).toBe(false);
  complete({ title: '首页' });
  await jest.advanceTimersByTimeAsync(20);
  expect(second.snapshot().data).toEqual({ title: '首页' });
  second.release();
  runtime.dispose();
  await jest.runOnlyPendingTimersAsync();
  jest.useRealTimers();
});

test('session scopes prevent a previous user query from becoming current data', async () => {
  jest.useFakeTimers();
  const execute = jest.fn().mockResolvedValue({ title: 'first' });
  const runtime = createAppStore({ execute });
  const client = createResourceClient(runtime);
  const a = client
    .source<{ title: string }>({ apiId: 'profile.summary' }, () => true, 1)
    .open();
  await jest.advanceTimersByTimeAsync(20);
  execute.mockResolvedValue({ title: 'second' });
  const b = client
    .source<{ title: string }>({ apiId: 'profile.summary' }, () => true, 2)
    .open();
  await jest.advanceTimersByTimeAsync(20);
  expect(a.snapshot().data?.title).toBe('first');
  expect(b.snapshot().data?.title).toBe('second');
  a.release();
  b.release();
  runtime.dispose();
  await jest.runOnlyPendingTimersAsync();
  jest.useRealTimers();
});
