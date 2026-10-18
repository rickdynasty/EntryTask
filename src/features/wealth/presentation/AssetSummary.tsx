/** EntryTask RN：财富业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Asset } from '@features/services/public';
import { Icon } from '@shared/ui/Icon';
import { ui } from '@shared/ui/Primitives';
import { formatMinor } from '@shared/formatting/numbers';
/** 按共享可见状态掩码金额，眼睛动作交由 Native 偏好处理。 */
export function AssetSummary({
  asset,
  visible,
  onToggle,
  onPress,
  compact = false,
}: {
  asset: Asset | null;
  visible: boolean;
  onToggle: () => void;
  onPress: () => void;
  compact?: boolean;
}) {
  const total =
    visible && asset ? formatMinor(asset.total.amountMinor) : '******';
  const income =
    visible && asset ? formatMinor(asset.income.amountMinor) : '******';
  return (
    <View style={[styles.box, compact && styles.compact]}>
      <View style={[ui.between, styles.metricsRow]}>
        <Pressable onPress={onPress} accessibilityLabel="查看资产总览">
          <Text style={styles.value}>{total}</Text>
          <View style={[ui.row, styles.assetLabelRow]}>
            <Text style={ui.body}>总资产</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={visible ? '隐藏资产' : '显示资产'}
              onPress={onToggle}
              hitSlop={10}
            >
              <Icon name="eye" size={18} color="#898782" />
            </Pressable>
          </View>
        </Pressable>
        {!compact && (
          <Pressable
            onPress={onPress}
            accessibilityLabel="查看最新收益"
            style={styles.right}
          >
            <Text style={styles.value}>{income}</Text>
            <Text style={[ui.body, styles.incomeLabel]}>最新收益</Text>
          </Pressable>
        )}
      </View>
      {visible && asset && (
        <Text style={[ui.muted, styles.asOf]}>
          截至 {asset.asOf} · 模拟资产
        </Text>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  compact: { paddingVertical: 2, paddingHorizontal: 0 },
  metricsRow: { alignItems: 'flex-start' },
  assetLabelRow: { gap: 6, marginTop: 9 },
  incomeLabel: { marginTop: 9 },
  asOf: { marginTop: 8 },
  box: { paddingVertical: 14, paddingHorizontal: 14 },
  value: { fontSize: 24, fontWeight: '600', color: '#242321' },
  right: { alignItems: 'flex-end' },
});
