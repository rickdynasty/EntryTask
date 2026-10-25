/** 根页组装不能在render触发Store/IO，换会话和卸载必须释放旧模型。 */
import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { useRootChannels } from '@app/navigation/useRootChannels';
import type { Application } from '@app/CompositionRoot';

test('creates channels after commit, reuses same epoch and disposes replaced ownership', async () => {
  let rendering = false;
  const models = new Map<number, ReturnType<Application['createChannels']>>();
  const application = {
    createChannels: jest.fn((epoch: number) => {
      expect(rendering).toBe(false);
      const channels = { dispose: jest.fn() } as unknown as ReturnType<Application['createChannels']>;
      models.set(epoch, channels);
      return channels;
    }),
  };
  const seen: Array<ReturnType<Application['createChannels']> | null> = [];
  function Probe({ epoch }: { epoch: number }) {
    rendering = true;
    const channels = useRootChannels(application, epoch);
    seen.push(channels);
    rendering = false;
    return null;
  }
  let root!: Renderer.ReactTestRenderer;
  try {
    await act(async () => { root = Renderer.create(<Probe epoch={1} />); });
    expect(application.createChannels).toHaveBeenCalledTimes(1);
    expect(seen.at(-1)).toBe(models.get(1));
    await act(async () => { root.update(<Probe epoch={1} />); });
    expect(application.createChannels).toHaveBeenCalledTimes(1);
    seen.length = 0;
    await act(async () => { root.update(<Probe epoch={2} />); });
    expect(seen[0]).toBeNull();
    expect(seen.at(-1)).toBe(models.get(2));
    expect(models.get(1)!.dispose).toHaveBeenCalledTimes(1);
    expect(models.get(2)!.dispose).not.toHaveBeenCalled();
  } finally {
    await act(async () => { root?.unmount(); });
  }
  expect(models.get(2)!.dispose).toHaveBeenCalledTimes(1);
});
