/** 名片和财务分卡组合；所有金额经同一MoneyText打码。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Identity } from '@features/services/public';
import type { FinancialOverview } from '../../domain/Profile';
import { Artwork, Surface, ui } from '@shared/ui/Primitives';
import { MoneyText } from '@shared/ui/DashboardComponents';
import { formatRatio, displayMoneyRatio } from '@shared/formatting/numbers';
export function UserCardHeader({
  data,
  onStat,
}: {
  data: Identity | null;
  onStat: (title: string) => void;
}) {
  const stats = [
    ['银行卡', data?.bankCards],
    ['待办', data?.todos],
    ['卡券', data?.coupons],
    ['积分', data?.points],
    ['能量值', data?.energy],
  ] as const;
  return (
    <Surface>
      <View style={styles.identity}>
        <Artwork name="profile-avatar" style={styles.avatar} />
        <View>
          <Text style={styles.name}>{data?.nickname ?? '正在加载资料'}</Text>
          <Text style={styles.green}>{data?.carbonLabel ?? '低碳'}</Text>
          <Text style={ui.muted}>
            R{data?.level ?? '—'} {data?.tierName}
          </Text>
        </View>
      </View>
      <View style={ui.row}>
        {stats.map(([title, value]) => (
          <Pressable
            key={title}
            accessibilityRole="button"
            onPress={() => onStat(title)}
            style={styles.stat}
          >
            <Text style={styles.number}>{value ?? '—'}</Text>
            <Text style={styles.label}>{title}</Text>
          </Pressable>
        ))}
      </View>
    </Surface>
  );
}
export function MonthlyCashflowCard({
  data,
  visible,
}: {
  data: FinancialOverview | null;
  visible: boolean;
}) {
  const fraction = data
    ? displayMoneyRatio(data.monthlyIncome, data.monthlyExpense, true)
    : null;
  return (
    <Surface>
      <Text style={ui.heading}>月度收支</Text>
      <Text style={ui.muted}>{data?.month ?? '—'} · 本地演示</Text>
      <View style={styles.metrics}>
        <View>
          <MoneyText value={data?.monthlyIncome} visible={visible} />
          <Text style={ui.muted}>本月收入</Text>
        </View>
        <View>
          <MoneyText value={data?.monthlyExpense} visible={visible} />
          <Text style={ui.muted}>本月支出</Text>
        </View>
      </View>
      <View
        accessibilityLabel={visible ? '本地账户暂无收支' : '收支比例已隐藏'}
        style={styles.neutralTrack}
      >
        {visible && fraction !== null && (
          <View style={[styles.ratioFill, { width: `${fraction * 100}%` }]} />
        )}
      </View>
    </Surface>
  );
}
export function CreditBillCard({
  data,
  visible,
  onOpen,
}: {
  data: FinancialOverview | null;
  visible: boolean;
  onOpen: () => void;
}) {
  return (
    <Surface>
      <Text style={ui.heading}>信用卡账单</Text>
      {data?.creditBill.hasCard ? (
        <>
          <MoneyText value={data.creditBill.unpaid} visible={visible} />
          <Text style={ui.muted}>
            到期日 {visible ? data.creditBill.dueDate ?? '—' : '已隐藏'}
          </Text>
        </>
      ) : (
        <Text style={ui.muted}>
          {data ? '尚未绑定信用卡，暂无账单' : '账单信息暂不可用'}
        </Text>
      )}
      <Pressable
        accessibilityRole="button"
        onPress={onOpen}
        style={styles.action}
      >
        <Text style={ui.body}>信用卡服务 ›</Text>
      </Pressable>
    </Surface>
  );
}
export function LoanSummaryCard({
  data,
  visible,
  onOpen,
}: {
  data: FinancialOverview | null;
  visible: boolean;
  onOpen: () => void;
}) {
  return (
    <Surface>
      <Text style={ui.heading}>我的借钱</Text>
      <View style={styles.metrics}>
        <View>
          <MoneyText value={data?.loanOffer.limit} visible={visible} />
          <Text style={ui.muted}>营销演示上限 · 尚未授信</Text>
        </View>
        <View>
          <Text style={styles.number}>
            {data ? formatRatio(data.loanOffer.annualRate) : '—'}
          </Text>
          <Text style={ui.muted}>示例年利率</Text>
        </View>
      </View>
      <Pressable
        accessibilityRole="button"
        onPress={onOpen}
        style={styles.action}
      >
        <Text style={ui.body}>了解借款服务 ›</Text>
      </Pressable>
    </Surface>
  );
}
const styles = StyleSheet.create({
  identity: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 12,
  },
  avatar: { width: 57, height: 57 },
  name: { fontSize: 21, color: '#292623', fontWeight: '600' },
  green: { color: '#57A787', fontSize: 11, paddingVertical: 5 },
  stat: {
    flex: 1,
    minHeight: 62,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
  },
  number: { fontSize: 21, color: '#292623' },
  label: { fontSize: 12, color: '#44413C' },
  metrics: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: 14,
    gap: 8,
    flexWrap: 'wrap',
  },
  neutralTrack: {
    height: 5,
    borderRadius: 3,
    backgroundColor: '#E8E4E0',
    marginTop: 14,
  },
  ratioFill: { height: 5, backgroundColor: '#B78C5D' },
  action: { minHeight: 48, justifyContent: 'center' },
});
