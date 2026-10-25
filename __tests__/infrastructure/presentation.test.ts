/** EntryTask RN 测试：覆盖金额展示、权益操作状态与列表详情目标映射。 */
import { formatMinor, formatRatio } from '@shared/formatting/numbers';
import { projectDocument } from '@features/services/public';
test('money formatting keeps large minor-unit strings exact', () => {
  expect(formatMinor('900719925474099312')).toBe('9,007,199,254,740,993.12');
  expect(formatMinor('-7')).toBe('-0.07');
  expect(formatRatio('-0.0012')).toBe('-0.12%');
});
test('benefit and checkin actions reflect authoritative Native state', () => {
  const benefit = projectDocument(
    { kind: 'benefit', title: '权益', requiresAuth: false },
    { status: 'claimed' },
  );
  expect(benefit.actionDisabled).toBe(true);
  expect(benefit.actionLabel).toBe('已领取');
  expect(benefit.metrics).toContainEqual({
    label: '当前状态',
    value: '已领取',
  });
  const day = projectDocument(
    { kind: 'checkin', title: '签到', requiresAuth: true },
    { checkedToday: true, businessDate: '2026-10-18' },
  );
  expect(day.actionDisabled).toBe(true);
  expect(day.businessDate).toBe('2026-10-18');
});

test('claim list retains the protected detail route and stable claim identity', () => {
  const page = projectDocument(
    { kind: 'claims', title: '已领权益', requiresAuth: true },
    { items: [{ id: 'claim-book', title: '图书权益', status: 'claimed' }] },
  );
  expect(page.rows[0]?.target).toEqual({
    kind: 'claim',
    title: '图书权益',
    id: 'claim-book',
    requiresAuth: true,
  });
  expect(page.rows[0]?.value).toBe('已领取');
});

test('display proportions preserve units, tolerate zero and never overflow large balances', () => {
  const { displayMoneyRatio } = require('@shared/formatting/numbers');
  expect(displayMoneyRatio({ amountMinor: '0', minorUnit: 2 }, { amountMinor: '0', minorUnit: 2 })).toBeNull();
  expect(displayMoneyRatio({ amountMinor: '100', minorUnit: 2 }, { amountMinor: '2', minorUnit: 0 })).toBe(0.5);
  expect(displayMoneyRatio({ amountMinor: '9'.repeat(400), minorUnit: 2 }, { amountMinor: '9'.repeat(400), minorUnit: 2 }, true)).toBe(0.5);
});
