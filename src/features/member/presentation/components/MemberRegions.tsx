import { displayMoneyRatio } from '@shared/formatting/numbers';
/** 会员区域独立封装；不推导奖励规则，不持有独立隐私状态。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Checkin, Identity } from '@features/services/public';
import { PrimaryButton, Surface, ui } from '@shared/ui/Primitives';
import { MoneyText } from '@shared/ui/DashboardComponents';
import { Icon } from '@shared/ui/Icon';
export function VipProfileCard({
  identity,
  visible,
  onBenefits,
}: {
  identity: Identity | null;
  visible: boolean;
  onBenefits: () => void;
}) {
  const progress = identity?.tierProgress;
  const fraction = progress?.isTopTier
    ? 1
    : progress
    ? displayMoneyRatio(progress.current, progress.nextThreshold)
    : null;
  return (
    <View style={styles.vip}>
      <View style={ui.between}>
        <View style={[ui.row, styles.identityTitle]}>
          <Icon name="crown" size={42} color="#F6DCA0" />
          <Text style={styles.tier}>
            R{identity?.level ?? '—'} {identity?.tierName ?? '中信红会员'}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={onBenefits}
          style={styles.all}
        >
          <Text style={styles.benefitText}>全部权益 ›</Text>
        </Pressable>
      </View>
      <View
        accessibilityLabel={visible ? '会员升级进度' : '会员升级进度已隐藏'}
        style={styles.track}
      >
        <View
          style={[
            styles.progress,
            { width: `${visible ? (fraction ?? 0) * 100 : 0}%` },
          ]}
        />
      </View>
      <Text style={styles.caption}>当前月日均金融资产 · 演示口径</Text>
      <MoneyText
        value={identity?.tierProgress.current}
        visible={visible}
        color="#FFF3C3"
      />
      {identity?.tierProgress.isTopTier && (
        <Text style={styles.caption}>已达最高演示等级</Text>
      )}
    </View>
  );
}
export function InteractiveTaskCard({
  data,
  operation,
  onSubmit,
  refreshing,
}: {
  data: Checkin | null;
  operation: { submitting: boolean; message: string };
  onSubmit: () => void;
  refreshing: boolean;
}) {
  return (
    <Surface style={styles.task}>
      <View style={ui.between}>
        <View style={styles.taskTitle}>
          <Text style={ui.body}>
            连续签到赢好礼，已连签{data?.consecutiveDays ?? '—'}天
          </Text>
        </View>
        <PrimaryButton
          small
          title={data?.checkedToday ? '今日已签到' : '立即签到'}
          disabled={!data || data.checkedToday || refreshing}
          loading={operation.submitting}
          onPress={onSubmit}
        />
      </View>
      <Text style={ui.muted}>{data?.rewardRule ?? '正在加载签到规则'}</Text>
      <View style={styles.days}>
        {data?.calendar.map(day => (
          <View
            key={day.date}
            accessible
            accessibilityLabel={`${day.date}，${
              day.checked ? '已签到' : '未签到'
            }`}
            style={styles.day}
          >
            <Text style={styles.check}>{day.checked ? '已签' : '空'}</Text>
            <Text style={styles.date}>{day.date.slice(5)}</Text>
          </View>
        ))}
      </View>
      {operation.message !== '' && (
        <Text accessibilityLiveRegion="polite" style={ui.muted}>
          {operation.message}
        </Text>
      )}
    </Surface>
  );
}
const styles = StyleSheet.create({
  identityTitle: { flex: 1 },
  vip: { backgroundColor: '#159BC5', borderRadius: 8, padding: 16, gap: 7 },
  tier: { color: '#FFF3C3', fontSize: 20, fontWeight: '700', flexShrink: 1 },
  caption: { color: '#E1F6FF', fontSize: 12 },
  all: { minHeight: 48, justifyContent: 'center', paddingLeft: 4 },
  benefitText: { color: '#FFF0CF', fontSize: 12 },
  track: { height: 2, backgroundColor: '#9BD5E9', marginTop: 6 },
  progress: { height: 2, backgroundColor: '#FFF3C3' },
  task: { backgroundColor: '#FFF8F4', gap: 12 },
  taskTitle: { flex: 1, paddingRight: 8 },
  days: { flexDirection: 'row', gap: 4 },
  day: {
    flex: 1,
    backgroundColor: '#FFF',
    alignItems: 'center',
    paddingVertical: 14,
    gap: 10,
  },
  check: { color: '#9C763F', fontSize: 12 },
  date: { fontSize: 9, color: '#A78C6E' },
});
