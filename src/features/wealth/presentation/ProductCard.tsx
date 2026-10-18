/** EntryTask RN：财富业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ui } from '@shared/ui/Primitives';
import { formatRatio } from '@shared/formatting/numbers';
import type { Product } from '../domain/Wealth';
/** 展示产品指标和风险提示，不将演示数据描述为收益承诺。 */
export function ProductCard({
  product,
  onPress,
}: {
  product: Product;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={product.name}
      onPress={onPress}
      style={styles.card}
    >
      <View style={ui.row}>
        <Text numberOfLines={1} style={styles.name}>
          {product.name}
        </Text>
        <Text style={styles.tag}>{product.distribution}</Text>
      </View>
      <View style={[ui.row, styles.metricsRow]}>
        <View>
          <Text
            style={[
              styles.value,
              product.ratio.startsWith('-') && styles.negativeValue,
            ]}
          >
            {formatRatio(product.ratio)}
          </Text>
          <Text style={ui.muted}>{product.indicatorLabel}</Text>
        </View>
        <View>
          <Text style={styles.term}>{product.term}</Text>
          <Text style={ui.muted}>{product.minimum}</Text>
        </View>
      </View>
      <Text style={styles.risk}>{product.riskText}</Text>
    </Pressable>
  );
}
const styles = StyleSheet.create({
  metricsRow: { gap: 28, marginTop: 15 },
  negativeValue: { color: '#268262' },
  card: { backgroundColor: '#FFF', borderRadius: 10, padding: 17, gap: 8 },
  name: { flex: 1, fontSize: 16, color: '#242321' },
  tag: {
    fontSize: 10,
    color: '#A78052',
    borderWidth: 0.5,
    borderColor: '#C9B999',
    paddingHorizontal: 4,
    borderRadius: 2,
    marginLeft: 8,
  },
  value: { fontSize: 28, fontWeight: '600', color: '#EE343D' },
  term: { fontSize: 18, color: '#242321', marginBottom: 5 },
  risk: { fontSize: 10, color: '#9C9690', marginTop: 8 },
});
