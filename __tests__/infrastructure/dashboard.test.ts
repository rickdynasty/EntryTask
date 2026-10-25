import { HomePageViewModel } from '@features/home/presentation/HomePageViewModel';
/** 验证会话UI状态及签到取消/结果确认，避免只覆盖静态文案。 */
import { createAppStore } from '@infra/state/createAppStore';
import { MemberViewModel } from '@features/member/presentation/MemberViewModel';
import type { ResourceSource } from '@shared/domain/Resource';
import type { Checkin, CheckinRepository } from '@features/services/public';
function source<T>(data: T | null, loading = false): ResourceSource<T> {
  return {
    open: () => ({
      snapshot: () => ({ data, loading, error: null }),
      subscribe: () => () => {},
      refresh: jest.fn(),
      release: jest.fn(),
    }),
  };
}
const status: Checkin = {
  businessDate: '2026-10-25',
  checkedToday: false,
  consecutiveDays: 0,
  energy: 0,
  awardedEnergy: 0,
  alreadyChecked: false,
  rewardDays: [3, 7],
  calendar: [],
  rewardRule: 'demo',
};
function model(commands: CheckinRepository, loading = false) {
  const vm = new MemberViewModel(
    {
      content: source<never>(null),
      summary: source<never>(null),
      checkin: source(status, loading),
    },
    commands,
  );
  vm.checkin.activate();
  vm.activateSubmission();
  return vm;
}
test('client page state survives same-epoch tabs and rejects an old owner after reset', () => {
  const runtime = createAppStore({ execute: jest.fn() });
  const old = runtime.pageState(1, 2);
  old.update({ category: 'hot', offset: 400 });
  expect(runtime.pageState(1, 2).getSnapshot()).toEqual({
    category: 'hot',
    offset: 400,
  });
  runtime.resetPages();
  const next = runtime.pageState(2, 2);
  old.update({ offset: 999 });
  expect(next.getSnapshot()).toEqual({ category: 'fund', offset: 0 });
  expect(old.getSnapshot().offset).toBe(0);
  runtime.dispose();
});
test('double tap sends one write; confirmed success cannot replay while query refresh is pending', async () => {
  let complete: (value: Checkin) => void = () => {};
  const commands = {
    submit: jest.fn(
      () =>
        new Promise<Checkin>(resolve => {
          complete = resolve;
        }),
    ),
    status: jest.fn(),
  };
  const vm = model(commands);
  const first = vm.submit();
  await vm.submit();
  expect(commands.submit).toHaveBeenCalledTimes(1);
  complete({ ...status, checkedToday: true });
  await first;
  await vm.submit();
  expect(commands.submit).toHaveBeenCalledTimes(1);
  vm.dispose();
});
test('ambiguous failure is confirmed before another write and never automatically replayed', async () => {
  const commands = {
    submit: jest.fn().mockRejectedValue(new Error('timeout')),
    status: jest
      .fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ ...status, checkedToday: true }),
  };
  const vm = model(commands);
  await vm.submit();
  await vm.submit();
  expect(commands.submit).toHaveBeenCalledTimes(1);
  expect(commands.status).toHaveBeenCalledTimes(2);
  expect(vm.getOperation().message).toContain('已确认');
  vm.dispose();
});
test('leaving the page aborts the write and late replies cannot change operation state', async () => {
  let complete: (value: Checkin) => void = () => {};
  const commands = {
    submit: jest.fn(
      (_date: string, _key: string, _signal: AbortSignal) =>
        new Promise<Checkin>(resolve => {
          complete = resolve;
        }),
    ),
    status: jest.fn(),
  };
  const vm = model(commands);
  const operation = vm.submit();
  vm.deactivateSubmission();
  expect(commands.submit.mock.calls[0]?.[2].aborted).toBe(true);
  complete({ ...status, checkedToday: true });
  await operation;
  expect(vm.getOperation()).toEqual({ submitting: false, message: '' });
  vm.dispose();
});

test('refreshing an old business date cannot start another checkin write', async () => {
  const commands = { submit: jest.fn(), status: jest.fn() };
  const vm = model(commands, true);
  await vm.submit();
  expect(commands.submit).not.toHaveBeenCalled();
  vm.dispose();
});

// 主页分类只保存客户端选择，切换时释放旧查询；重复选择不再次创建请求。
test('home category replaces one query, retains tab selection and resets for a new session', () => {
  const runtime = createAppStore({ execute: jest.fn() });
  const ui = runtime.pageState(1, 1);
  expect(ui.getSnapshot().category).toBe('all');
  const release = jest.fn();
  const first: ResourceSource<never> = {
    open: () => ({
      snapshot: () => ({ data: null, loading: true, error: null }),
      subscribe: () => () => {},
      refresh: jest.fn(),
      release,
    }),
  };
  const next = jest.fn(() => source<never>(null));
  const vm = new HomePageViewModel(
    { content: source<never>(null), products: first },
    ui,
    next,
  );
  vm.products.activate();
  vm.selectCategory('finance');
  vm.selectCategory('finance');
  expect(next).toHaveBeenCalledTimes(1);
  expect(release).toHaveBeenCalledTimes(1);
  vm.dispose();
  expect(runtime.pageState(1, 1).getSnapshot().category).toBe('finance');
  runtime.resetPages();
  expect(runtime.pageState(2, 1).getSnapshot().category).toBe('all');
  runtime.dispose();
});


test('inactive category replacement never opens IO and notifies subscribers of reset', () => {
  const runtime = createAppStore({ execute: jest.fn() });
  const open = jest.fn(() => source<never>(null).open());
  const vm = new HomePageViewModel(
    { content: source<never>(null), products: source<never>(null) },
    runtime.pageState(1, 1),
    () => ({ open }),
  );
  const changed = jest.fn();
  vm.products.subscribe(changed);
  vm.selectCategory('finance');
  expect(open).not.toHaveBeenCalled();
  expect(changed).toHaveBeenCalledTimes(1);
  vm.products.activate();
  expect(open).toHaveBeenCalledTimes(1);
  vm.dispose();
  runtime.dispose();
});

test('late failure from an old write cannot poison a newer confirmed checkin', async () => {
  let rejectOld: (reason: Error) => void = () => {};
  const commands = {
    submit: jest.fn(() => new Promise<Checkin>((_resolve, reject) => { rejectOld = reject; })),
    status: jest.fn().mockResolvedValue({ ...status, checkedToday: false }),
  };
  const vm = model(commands);
  const first = vm.submit();
  vm.deactivateSubmission();
  vm.activateSubmission();
  // 离页后的不确定写入先确认；查询返回未签到后才允许用户再提交。
  await vm.submit();
  expect(commands.status).toHaveBeenCalledTimes(1);
  rejectOld(new Error('late failure'));
  await first;
  commands.submit.mockResolvedValueOnce({ ...status, checkedToday: true });
  await vm.submit();
  expect(commands.submit).toHaveBeenCalledTimes(2);
  expect(commands.status).toHaveBeenCalledTimes(1);
  expect(vm.getOperation().message).toContain('签到成功');
  vm.dispose();
  await vm.submit();
  expect(commands.submit).toHaveBeenCalledTimes(2);
});
