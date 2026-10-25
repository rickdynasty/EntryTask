/** EntryTask RN 测试：覆盖 Native Shell revision 顺序、会话切换与用户偏好隔离。 */
import { ShellViewModel } from '@app/navigation/ShellViewModel';
import type { HostPort } from '@platform/ports/HostPort';
const host: HostPort = {
  observeShell: jest.fn(() => () => {}),
  command: jest.fn().mockResolvedValue(undefined),
  readVisibility: jest.fn().mockResolvedValue(true),
  writeVisibility: jest.fn().mockResolvedValue(undefined),
};
test('Native revisions order tab events and epochs clear private queries once', async () => {
  const clear = jest.fn();
  const shell = new ShellViewModel(host, { log: jest.fn() }, clear);
  shell.accept({
    tabId: 3,
    depth: 0,
    authenticated: true,
    sessionEpoch: 1,
    revision: 3,
  });
  shell.accept({
    tabId: 2,
    depth: 0,
    authenticated: false,
    sessionEpoch: 0,
    revision: 2,
  });
  expect(shell.getSnapshot().tabId).toBe(3);
  expect(clear).toHaveBeenCalledTimes(1);
  await Promise.resolve();
  expect(shell.getSnapshot().assetsVisible).toBe(false);
  expect(host.readVisibility).not.toHaveBeenCalled();
  shell.toggleVisibility();
  expect(shell.getSnapshot().assetsVisible).toBe(true);
  shell.toggleVisibility();
  expect(shell.getSnapshot().assetsVisible).toBe(false);
  expect(host.writeVisibility).not.toHaveBeenCalled();
  shell.accept({
    tabId: 3,
    depth: 0,
    authenticated: false,
    sessionEpoch: 2,
    revision: 4,
  });
  expect(shell.getSnapshot().assetsVisible).toBe(false);
  expect(clear).toHaveBeenCalledTimes(2);
  shell.dispose();
});
test('session-only visibility stays synchronized across tabs and resets on a new epoch', () => {
  const shell = new ShellViewModel(host, { log: jest.fn() }, jest.fn());
  shell.accept({
    tabId: 2,
    depth: 0,
    authenticated: true,
    sessionEpoch: 1,
    revision: 1,
  });
  shell.toggleVisibility();
  shell.accept({
    tabId: 4,
    depth: 0,
    authenticated: true,
    sessionEpoch: 1,
    revision: 2,
  });
  expect(shell.getSnapshot().assetsVisible).toBe(true);
  shell.accept({
    tabId: 4,
    depth: 0,
    authenticated: true,
    sessionEpoch: 2,
    revision: 3,
  });
  expect(shell.getSnapshot().assetsVisible).toBe(false);
});

test('late Session snapshots and host events cannot roll back a newer login epoch', () => {
  const shell = new ShellViewModel(host, { log: jest.fn() }, jest.fn());
  shell.accept({
    tabId: 4,
    depth: 0,
    authenticated: false,
    sessionEpoch: 2,
    revision: 6,
  });
  shell.acceptSession({ authenticated: true, sessionEpoch: 1 });
  shell.accept({
    tabId: 1,
    depth: 0,
    authenticated: true,
    sessionEpoch: 1,
    revision: 7,
  });
  expect(shell.getSnapshot()).toMatchObject({
    tabId: 4,
    authenticated: false,
    sessionEpoch: 2,
    revision: 6,
  });
  shell.acceptSession({ authenticated: false, sessionEpoch: 3 });
  expect(shell.getSnapshot().sessionEpoch).toBe(3);
  expect(shell.getSnapshot().revision).toBe(6);
  shell.dispose();
});
