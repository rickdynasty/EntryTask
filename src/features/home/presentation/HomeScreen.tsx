import React from 'react';
import {
  Button,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { AsyncSection } from '@shared/ui/AsyncSection';
import { theme } from '@shared/ui/theme';
import type { HomeState } from './HomeViewModel';

export function HomeScreen({
  state,
  refresh,
  selectService,
}: {
  state: HomeState;
  refresh: () => void;
  selectService: (id: string | null) => void;
}) {
  return (
    <ScrollView
      style={styles.page}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl
          refreshing={state.content.loading && !!state.content.data}
          onRefresh={refresh}
        />
      }
    >
      <View style={styles.card}>
        <Text style={styles.title}>
          {state.content.data?.title ?? 'EntryTask'}
        </Text>
        <Text style={styles.muted}>React Native · MVVM · 单 RN instance</Text>
        <Text style={styles.notice}>模拟数据，仅用于工程演示</Text>
      </View>
      <View style={styles.card}>
        <AsyncSection
          loading={state.content.loading}
          error={state.content.error}
          onRetry={refresh}
        >
          <Text style={styles.section}>常用服务</Text>
          {state.content.data?.services.map(service => (
            <Button
              key={service.id}
              title={service.title}
              onPress={() => selectService(service.id)}
            />
          ))}
          {state.selectedService && (
            <View accessibilityRole="alert">
              <Text style={styles.muted}>
                服务演示：
                {
                  state.content.data?.services.find(
                    service => service.id === state.selectedService,
                  )?.title
                }
                。当前阶段提供工程样板，未接入真实银行交易。
              </Text>
              <Button title="关闭说明" onPress={() => selectService(null)} />
            </View>
          )}
        </AsyncSection>
      </View>
      <View style={styles.card}>
        <Text style={styles.section}>财富精选 · 本地 Mock</Text>
        <AsyncSection
          loading={state.products.loading}
          error={state.products.error}
          onRetry={refresh}
        >
          {state.products.data?.items.map(product => (
            <View style={styles.product} key={product.id}>
              <Text style={styles.name}>{product.name}</Text>
              <Text style={styles.muted}>{product.riskText}</Text>
            </View>
          ))}
          {state.products.data && state.products.data.items.length === 0 && (
            <Text>暂无产品</Text>
          )}
        </AsyncSection>
      </View>
    </ScrollView>
  );
}
const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: theme.colors.background },
  content: { padding: 16, gap: 16 },
  card: {
    padding: 20,
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius,
    gap: 8,
  },
  title: { fontSize: 25, fontWeight: '700', color: theme.colors.text },
  section: { fontSize: 19, fontWeight: '600', color: theme.colors.text },
  muted: { fontSize: 13, color: theme.colors.muted, lineHeight: 21 },
  notice: { color: theme.colors.accent, fontSize: 13 },
  product: {
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#DDD',
  },
  name: { fontSize: 16, color: theme.colors.text },
});
