/** EntryTask RN：通用服务与详情业务域模块：负责数据映射与访问实现，不跨域持有页面私有实现。 */
import type { Document, DocumentRow } from '../domain/Document';
import type { Target, TargetKind } from '../domain/Content';
import { formatMinor, formatRatio } from '@shared/formatting/numbers';
const text = (value: unknown): string =>
  typeof value === 'string'
    ? value
    : typeof value === 'number'
    ? String(value)
    : '';
const object = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
const paragraphs = (value: unknown): string[] =>
  Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
const detailKind: Partial<Record<TargetKind, TargetKind>> = {
  products: 'product',
  accounts: 'account',
  transactions: 'transaction',
  claims: 'claim',
  items: 'item',
  articles: 'article',
  activities: 'activity',
  benefits: 'benefit',
  messages: 'message',
};
const statusText = (value: unknown): string =>
  ({
    available: '可领取',
    claimed: '已领取',
    ineligible: '等级不符合',
    soldOut: '已领完',
    expired: '已过期',
  }[text(value)] ?? text(value));
const labels: Record<string, string> = {
  nickname: '昵称',
  phoneMasked: '手机号',
  level: '演示等级',
  bankCards: '银行卡',
  todos: '待办',
  coupons: '卡券',
  points: '积分',
  energy: '能量值',
  asOf: '数据截至',
  period: '统计期间',
  minimum: '起购金额',
  term: '产品期限',
  riskLevel: '风险等级',
  numberMasked: '账户号码',
  status: '当前状态',
  date: '日期',
  balance: '能量余额',
  consecutiveDays: '连续签到天数',
  businessDate: '业务日期',
};
export function projectDocument(
  target: Target,
  raw: Record<string, unknown>,
): Document {
  // 将经 Schema 校验的 API 数据映射为通用详情行、金额、状态和受控跳转目标。
  const rows: DocumentRow[] = (Array.isArray(raw.items) ? raw.items : []).map(
    (value, index) => {
      const item = object(value);
      const id = text(item.id) || String(index);
      const kind = detailKind[target.kind];
      const rowTarget =
        item.target && typeof item.target === 'object'
          ? (item.target as Target)
          : kind
          ? {
              kind,
              title: text(item.title) || text(item.name),
              id,
              requiresAuth: target.requiresAuth,
            }
          : undefined;
      return {
        id,
        title: text(item.title) || text(item.name),
        subtitle: [
          text(item.subtitle) || text(item.summary) || text(item.riskText),
          text(item.date),
        ]
          .filter(Boolean)
          .join('\n'),
        value: item.ratio
          ? formatRatio(text(item.ratio))
          : item.amountMinor
          ? `${item.direction === 'out' ? '-' : '+'}${formatMinor(
              text(item.amountMinor),
            )}`
          : statusText(item.status),
        ...(item.assetKey ? { assetKey: text(item.assetKey) } : {}),
        ...(rowTarget ? { target: rowTarget } : {}),
        ...(typeof item.isRead === 'boolean' ? { read: item.isRead } : {}),
      };
    },
  );
  const metrics: { label: string; value: string }[] = [];
  for (const key of ['total', 'liabilities', 'net', 'income']) {
    const money = object(raw[key]);
    if (money.amountMinor)
      metrics.push({
        label:
          {
            total: '总资产',
            liabilities: '总负债',
            net: '净资产',
            income: '最新收益',
          }[key] ?? key,
        value: '¥' + formatMinor(text(money.amountMinor)),
      });
  }
  for (const [key, label] of Object.entries(labels)) {
    if (raw[key] !== undefined && typeof raw[key] !== 'object')
      metrics.push({
        label,
        value: key === 'status' ? statusText(raw[key]) : text(raw[key]),
      });
  }
  if (raw.ratio)
    metrics.unshift({
      label: text(raw.indicatorLabel),
      value: formatRatio(text(raw.ratio)),
    });
  const body = paragraphs(raw.paragraphs).length
    ? paragraphs(raw.paragraphs)
    : paragraphs(raw.description);
  if (target.kind === 'checkin')
    body.push(
      '本原型仅在连续签到第3天奖励30能量值，第7天奖励100能量值。普通日基础奖励为0。',
      '七天为一周期，断签后重新计算；以 Native 业务日为准。',
    );
  const actionLabel =
    target.kind === 'checkin'
      ? raw.checkedToday
        ? '今日已签到'
        : '立即签到'
      : target.kind === 'benefit'
      ? {
          claimed: '已领取',
          ineligible: '等级不符合',
          soldOut: '已领完',
          expired: '已过期',
        }[text(raw.status)] ?? '立即领取'
      : undefined;
  return {
    title: text(raw.title) || text(raw.name) || target.title,
    subtitle: text(raw.summary) || text(raw.subtitle),
    paragraphs: body,
    metrics,
    rows,
    hasMore: raw.hasMore === true,
    page: typeof raw.page === 'number' ? raw.page : 1,
    ...(typeof raw.businessDate === 'string'
      ? { businessDate: raw.businessDate }
      : {}),
    ...(typeof raw.checkedToday === 'boolean'
      ? { checkedToday: raw.checkedToday }
      : {}),
    ...(actionLabel
      ? {
          actionLabel,
          actionDisabled:
            target.kind === 'checkin'
              ? raw.checkedToday === true
              : raw.status !== 'available',
        }
      : {}),
  };
}
