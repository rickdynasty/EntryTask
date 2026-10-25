/** 财富页：资产、行情、宫格与最小分类结果；选择随VM资源及会话UI状态保持。 */
import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import { ChoiceRail, StatePanel, Surface, ui } from '@shared/ui/Primitives';
import {
  PageFrame,
  AssetPrivacyCard,
  MarketTickerCarousel,
} from '@shared/ui/DashboardComponents';
import type { ResourceState } from '@shared/domain/Resource';
import type { WealthContent, Product } from '../domain/Wealth';
import type { Asset, Page } from '@features/services/public';
export function WealthScreen({
  content,
  assets,
  products,
  category,
  active,
  actions,
  session,
  pageState,
  retryContent,
  retryAssets,
  retryProducts,
  onCategory,
}: {
  active: boolean;
  actions: PageActions;
  pageState: PageUiStatePort;
  session: PageSession;
  content: ResourceState<WealthContent>;
  assets: ResourceState<Asset>;
  products: ResourceState<Page<Product>>;
  category: string;
  retryContent: () => void;
  retryAssets: () => void;
  retryProducts: () => void;
  onCategory: (id: string) => void;
}) {
  return (
    <PageFrame
      ready={!!content.data && (!products.loading || !!products.data)}
      state={pageState}
      active={active}
      regions={[
        {
          id: 'wealth-assets',
          content: (
            <>
              <Text style={ui.heading}>财富</Text>
              <AssetPrivacyCard
                total={assets.data?.total}
                income={assets.data?.income}
                visible={session.assetsVisible}
                onToggle={actions.toggleVisibility}
              />
              <StatePanel
                loading={assets.loading && !assets.data}
                error={assets.error}
                onRetry={retryAssets}
              />
            </>
          ),
        },
        {
          id: 'wealth-market',
          content: (
            <MarketTickerCarousel
              items={content.data?.marketItems ?? []}
              onSelect={title =>
                actions.open({ kind: 'demo', title, requiresAuth: true })
              }
            />
          ),
        },
        {
          id: 'wealth-services',
          content: (
            <>
              <StatePanel
                loading={content.loading && !content.data}
                error={content.error}
                onRetry={retryContent}
              />
              {content.data && (
                <ServiceGrid
                  items={content.data.services}
                  onSelect={item => actions.open(item.target)}
                />
              )}
            </>
          ),
        },
        {
          id: 'wealth-categories',
          content: (
            <Surface>
              <ChoiceRail
                textOnly
                items={content.data?.categories ?? []}
                selected={category}
                onChange={id => {
                  onCategory(id);
                  actions.track('wealth', 'category.selected', id);
                }}
              />
              <StatePanel
                loading={products.loading && !products.data}
                error={products.error}
                empty={products.data?.items.length === 0}
                onRetry={retryProducts}
              />
              {products.data && (
                <>
                  <Text style={ui.muted}>
                    当前分类共{products.data.total}项 · 展示最多3项
                  </Text>
                  {products.data.items.slice(0, 3).map(item => (
                    <View key={item.id} style={styles.product}>
                      <Text style={ui.body}>{item.name}</Text>
                      <Text style={ui.muted}>
                        {item.term} · {item.riskLevel}
                      </Text>
                    </View>
                  ))}
                </>
              )}
            </Surface>
          ),
        },
      ]}
    />
  );
}

const styles = StyleSheet.create({ product: { paddingVertical: 12 } });
