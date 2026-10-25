/** 首页按视觉稿组合完整内容，资源与分类动作由Route注入。 */
import React from 'react';
import { ScrollView, Pressable, Text, View, StyleSheet } from 'react-native';
import type { Page } from '@features/services/public';
import { ProductCard, type Product } from '@features/wealth/public';
import { HomeEditorialSection } from './components/HomeEditorialSection';
import type { PageActions } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { ChannelHeader } from '@shared/ui/ChannelHeader';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import {
  StatePanel,
  Surface,
  SectionHeading,
  ChoiceRail,
  ui,
} from '@shared/ui/Primitives';
import { PageFrame, BannerCarousel } from '@shared/ui/DashboardComponents';
import type { ResourceState } from '@shared/domain/Resource';
import type { HomePage } from '../domain/Home';
export function HomeScreen({
  content,
  products,
  category,
  onCategory,
  retryProducts,
  active,
  actions,
  unread,
  pageState,
  retry,
  notificationError,
  onRetryNotifications,
}: {
  active: boolean;
  actions: PageActions;
  pageState: PageUiStatePort;
  content: ResourceState<HomePage>;
  products: ResourceState<Page<Product>>;
  category: string;
  onCategory: (id: string) => void;
  retryProducts: () => void;
  unread: number | undefined;
  retry: () => void;
  notificationError: string | null;
  onRetryNotifications: () => void;
}) {
  return (
    <PageFrame
      ready={
        (!content.loading || !!content.data) &&
        (!products.loading || !!products.data)
      }
      state={pageState}
      active={active}
      regions={[
        {
          id: 'home-top',
          content: (
            <ChannelHeader
              mode="home"
              actions={actions.header}
              placeholder={content.data?.searchPlaceholder ?? '搜索产品、服务'}
              unread={unread}
            />
          ),
        },
        ...(notificationError
          ? [
              {
                id: 'home-notifications',
                content: (
                  <StatePanel
                    loading={false}
                    error={notificationError}
                    onRetry={onRetryNotifications}
                  />
                ),
              },
            ]
          : []),
        {
          id: 'home-quick',
          content: (
            <>
              <StatePanel
                loading={content.loading && !content.data}
                error={content.error}
                onRetry={retry}
              />
              {content.data && (
                <ServiceGrid
                  items={content.data.quickServices}
                  columns={4}
                  gold
                  onSelect={item => actions.open(item.target)}
                />
              )}
            </>
          ),
        },
        {
          id: 'home-banner',
          content: (
            <BannerCarousel
              items={content.data?.banners ?? []}
              onSelect={item => actions.open(item.target)}
            />
          ),
        },
        {
          id: 'home-services',
          content: content.data && (
            <Surface>
              <ServiceGrid
                items={content.data.services}
                onSelect={item => actions.open(item.target)}
              />
            </Surface>
          ),
        },
        {
          id: 'home-shortcuts',
          content: (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.shortcuts}
            >
              {content.data?.shortcuts.map(item => (
                <Pressable
                  key={item.id}
                  accessibilityRole="button"
                  onPress={() => actions.open(item.target)}
                  style={styles.shortcut}
                >
                  <Text style={ui.muted}>{item.title}</Text>
                </Pressable>
              ))}
            </ScrollView>
          ),
        },
        ...(content.data?.sections.slice(0, 1).map(section => ({
          id: `home-section-${section.id}`,
          content: <HomeEditorialSection section={section} actions={actions} />,
        })) ?? []),
        {
          id: 'home-products',
          content: (
            <Surface style={styles.products}>
              <SectionHeading
                title="财富精选"
                subtitle="把握市场动向 优中选优"
              />
              <ChoiceRail
                items={content.data?.categories ?? []}
                selected={category}
                onChange={onCategory}
              />
              <StatePanel
                loading={products.loading && !products.data}
                error={products.error}
                empty={products.data?.items.length === 0}
                onRetry={retryProducts}
              />
              {products.data?.items.map(product => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onPress={() =>
                    actions.open({
                      kind: 'product',
                      title: product.name,
                      requiresAuth: true,
                      id: product.id,
                    })
                  }
                />
              ))}
              <View style={styles.moreRow}>
                <Pressable
                  accessibilityRole="button"
                  onPress={() =>
                    actions.open({
                      kind: 'products',
                      title: '查看更多产品',
                      requiresAuth: true,
                    })
                  }
                  style={styles.more}
                >
                  <Text style={ui.muted}>查看更多产品 ›</Text>
                </Pressable>
              </View>
            </Surface>
          ),
        },
        ...(content.data?.sections.slice(1).map(section => ({
          id: `home-section-${section.id}`,
          content: <HomeEditorialSection section={section} actions={actions} />,
        })) ?? []),
      ]}
    />
  );
}

const styles = StyleSheet.create({
  shortcuts: { gap: 8 },
  shortcut: {
    minHeight: 42,
    paddingHorizontal: 12,
    justifyContent: 'center',
    backgroundColor: '#FFF',
    borderRadius: 5,
  },
  products: { backgroundColor: '#FFF9F3' },
  moreRow: { alignItems: 'center' },
  more: { padding: 12 },
});
