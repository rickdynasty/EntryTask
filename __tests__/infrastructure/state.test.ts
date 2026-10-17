import { createAppStore } from '@infra/state/createAppStore';
import { createHomeRepository } from '@features/home/data/HomeRepositoryImpl';
import type { HomeContent } from '@features/home/domain/HomeRepository';

const data: HomeContent = {
  title: '首页',
  subtitle: '模拟',
  services: [],
  isDemo: true,
};

test('RTK Query shares one in-flight request; releasing one consumer does not cancel the other', async () => {
  jest.useFakeTimers();
  let complete: (value: unknown) => void = () => {};
  const execute = jest.fn(
    () =>
      new Promise<unknown>(resolve => {
        complete = resolve;
      }),
  );
  const runtime = createAppStore({ execute });
  const repository = createHomeRepository(runtime);
  const first = repository.content();
  const second = repository.content();
  expect(execute).toHaveBeenCalledTimes(1);
  first.release();
  complete(data);
  await jest.advanceTimersByTimeAsync(20);
  expect(second.snapshot().data).toEqual(data);
  second.release();
  runtime.dispose();
  await jest.runOnlyPendingTimersAsync();
  jest.useRealTimers();
});
