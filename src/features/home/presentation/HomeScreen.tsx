/** EntryTask RN：首页业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React, { useState } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { HomePageViewModel } from './HomePageViewModel';
import type { PageActions, PageSession } from '@features/services/public';
import { ProductCard } from '@features/wealth/public';
import { useResource } from '@shared/presentation/useResource';
import { ChannelHeader } from '@shared/ui/ChannelHeader';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import { CampaignCard } from '@shared/ui/CampaignCard';
import {
  Artwork,
  ChoiceRail,
  DemoFootnote,
  SectionHeading,
  StatePanel,
  Surface,
  ui,
} from '@shared/ui/Primitives';
/** 订阅当前可见资源并将点击动作交给页面路由。 */
export function HomeScreen({
  model,
  active,
  actions,
  unread,
}: {
  model: HomePageViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number;
}) {
  // 页面只订阅当前可见资源，滚动、筛选等短期交互状态保留在视图层。
  const content = useResource(model.content, active);
  const products = useResource(model.products, active);
  const [category, setCategory] = useState('all');
  const data = content.data;
  const refresh = () => {
    model.content.refresh();
    model.products.refresh();
  };
  return (
    <View style={styles.root}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={content.loading && !!data}
            onRefresh={refresh}
            tintColor="#BC8D58"
          />
        }
      >
        <View style={styles.hero}>
          <ChannelHeader
            mode="home"
            actions={actions.header}
            placeholder={data?.searchPlaceholder ?? '证券服务'}
            unread={unread}
          />
          {data && (
            <ServiceGrid
              items={data.quickServices}
              columns={4}
              gold
              onSelect={item => actions.open(item.target)}
            />
          )}
          <Pressable
            accessibilityLabel="金彩秋冬活动"
            onPress={() =>
              actions.open(
                data?.banners[0]?.target ?? {
                  kind: 'activities',
                  title: '金彩秋冬',
                  requiresAuth: false,
                },
              )
            }
            style={styles.heroCampaign}
          >
            <Artwork name="hero-ginkgo" style={styles.heroArt} />
            <View style={styles.heroCopy}>
              <Text style={styles.heroTitle}>金彩秋冬</Text>
              <Text style={styles.heroSubtitle}>私人银行 · 精选产品</Text>
              <Text style={styles.heroRisk}>
                理财、基金非存款，产品有风险，投资需谨慎
              </Text>
            </View>
          </Pressable>
        </View>
        <View style={styles.body}>
          <StatePanel
            loading={content.loading && !data}
            error={content.error}
            onRetry={refresh}
          />
          {data && (
            <>
              <ServiceGrid
                items={data.services}
                onSelect={item => actions.open(item.target)}
              />
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={styles.shortcuts}
              >
                {data.shortcuts.map(item => (
                  <Pressable
                    key={item.id}
                    onPress={() => actions.open(item.target)}
                    style={styles.shortcut}
                  >
                    <Text style={ui.body}>{item.title}</Text>
                  </Pressable>
                ))}
              </ScrollView>
              {data.banners[1] && (
                <CampaignCard
                  {...data.banners[1]}
                  onPress={() => actions.open(data.banners[1]!.target)}
                />
              )}
            </>
          )}
          <Surface style={styles.selection}>
            <SectionHeading
              title="财富精选"
              subtitle="把握市场动向 优中选优"
              onMore={() =>
                actions.open({
                  kind: 'products',
                  title: '财富精选',
                  category,
                  requiresAuth: false,
                })
              }
            />
            <ChoiceRail
              items={data?.categories ?? []}
              selected={category}
              onChange={value => {
                setCategory(value);
                actions.track('home', 'product.category.selected', value);
                model.selectCategory(value);
              }}
            />
            <StatePanel
              loading={products.loading && !products.data}
              error={products.error}
              empty={products.data?.items.length === 0}
              onRetry={model.products.refresh}
            >
              {products.data?.items.map(product => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onPress={() =>
                    actions.open({
                      kind: 'product',
                      title: product.name,
                      id: product.id,
                      requiresAuth: false,
                    })
                  }
                />
              ))}
            </StatePanel>
          </Surface>
          <DemoFootnote />
        </View>
      </ScrollView>
      <Pressable
        accessibilityLabel="前往财富广场"
        onPress={() => actions.switchTab(2)}
        style={styles.floating}
      >
        <Text style={styles.floatingText}>财富{'\n'}广场</Text>
      </Pressable>
    </View>
  );
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#F5F7F8' },
  scroll: { paddingBottom: 12 },
  hero: { backgroundColor: '#FCF0E5' },
  heroCampaign: { height: 133, overflow: 'hidden' },
  heroArt: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    width: '64%',
    height: 119,
  },
  heroCopy: { paddingLeft: 26, paddingTop: 12 },
  heroTitle: {
    fontSize: 28,
    fontFamily: 'serif',
    fontWeight: '700',
    color: '#915319',
    letterSpacing: 2,
  },
  heroSubtitle: { fontSize: 15, color: '#985C2B', marginTop: 6 },
  heroRisk: { fontSize: 8, color: '#B08061', marginTop: 36 },
  body: {
    marginTop: -1,
    paddingHorizontal: 12,
    gap: 14,
    borderTopLeftRadius: 18,
    borderTopRightRadius: 18,
    backgroundColor: '#F5F7F8',
    paddingTop: 3,
  },
  shortcuts: { gap: 9, paddingVertical: 3 },
  shortcut: {
    borderWidth: 1,
    borderColor: '#E1E5E8',
    borderRadius: 5,
    paddingHorizontal: 14,
    paddingVertical: 6,
  },
  selection: { backgroundColor: '#F6EFEB', gap: 12 },
  floating: {
    position: 'absolute',
    right: 0,
    top: '61%',
    backgroundColor: '#F5484C',
    paddingVertical: 10,
    paddingHorizontal: 5,
    borderTopLeftRadius: 10,
    borderBottomLeftRadius: 10,
  },
  floatingText: { color: '#FFF', fontSize: 13, lineHeight: 20 },
});
