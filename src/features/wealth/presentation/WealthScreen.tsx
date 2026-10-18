/** EntryTask RN：财富业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React, { useState } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import Svg, {
  Rect,
  Text as SvgText,
  Defs,
  LinearGradient,
  Stop,
} from 'react-native-svg';
import type { WealthViewModel } from './WealthViewModel';
import type { PageActions, PageSession } from '@features/services/public';
import { useResource } from '@shared/presentation/useResource';
import { ChannelHeader } from '@shared/ui/ChannelHeader';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import { CampaignCard } from '@shared/ui/CampaignCard';
import {
  ChoiceRail,
  DemoFootnote,
  PrimaryButton,
  StatePanel,
  Surface,
  ui,
} from '@shared/ui/Primitives';
import { Icon } from '@shared/ui/Icon';
import { ProductCard } from './ProductCard';
import { AssetSummary } from './AssetSummary';
/** 按分段和会话状态加载资讯、产品与资产资源。 */
export function WealthScreen({
  model,
  active,
  actions,
  session,
  unread,
}: {
  model: WealthViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number;
}) {
  // 财富、资讯与个人资产按可见区和会话状态分别订阅，避免游客读取私有数据。
  const [segment, setSegment] = useState('财富');
  const [category, setCategory] = useState('fund');
  const content = useResource(model.content, active);
  const products = useResource(model.products, active && segment === '财富');
  const assets = useResource(model.assets, active && session.authenticated);
  const articles = useResource(model.articles, active && segment === '资讯');
  const data = content.data;
  return (
    <ScrollView
      style={styles.page}
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.scroll}
      refreshControl={
        <RefreshControl
          refreshing={content.loading && !!data}
          onRefresh={() => {
            model.content.refresh();
            model.products.refresh();
            if (session.authenticated) model.assets.refresh();
          }}
        />
      }
    >
      <View style={styles.header}>
        <ChannelHeader
          mode="channel"
          actions={actions.header}
          tabs={['财富', '资讯']}
          selected={segment}
          onSelect={value => {
            actions.track('wealth', 'segment.selected', value);
            setSegment(value);
          }}
          unread={unread}
        />
        {segment === '财富' && (
          <AssetSummary
            asset={assets.data}
            visible={session.assetsVisible}
            onToggle={actions.toggleVisibility}
            onPress={() =>
              actions.open({
                kind: 'assets',
                title: '资产总览',
                requiresAuth: true,
              })
            }
          />
        )}
      </View>
      {segment === '资讯' ? (
        <View style={styles.body}>
          <StatePanel
            loading={articles.loading}
            error={articles.error}
            onRetry={model.articles.refresh}
            empty={articles.data?.items.length === 0}
          >
            {articles.data?.items.map(item => (
              <Surface key={item.id}>
                <Pressable
                  onPress={() =>
                    actions.open({
                      kind: 'article',
                      title: item.title,
                      id: item.id,
                      requiresAuth: false,
                    })
                  }
                >
                  <Text style={ui.heading}>{item.title}</Text>
                  <Text style={ui.muted}>{item.summary}</Text>
                  <Text style={ui.muted}>{item.date}</Text>
                </Pressable>
              </Surface>
            ))}
          </StatePanel>
        </View>
      ) : (
        <View style={styles.body}>
          {data && (
            <>
              <Pressable
                onPress={() =>
                  actions.open({
                    kind: 'article',
                    title: '市场解读',
                    id: data.market.id,
                    requiresAuth: false,
                  })
                }
                style={styles.market}
              >
                <Text style={styles.marketLabel}>市场{'\n'}解读</Text>
                <Text numberOfLines={2} style={[ui.body, styles.marketCopy]}>
                  {data.market.title}
                </Text>
                <Icon name="chevron" size={20} color="#F43E50" />
              </Pressable>
              <ServiceGrid
                items={data.services}
                onSelect={item => actions.open(item.target)}
              />
            </>
          )}
          <StatePanel
            loading={content.loading && !data}
            error={content.error}
            onRetry={model.content.refresh}
          />
          <Surface>
            <ChoiceRail
              textOnly
              items={data?.categories ?? []}
              selected={category}
              onChange={id => {
                setCategory(id);
                actions.track('wealth', 'product.category.selected', id);
                model.selectCategory(id);
              }}
            />
            {category === 'fund' ? (
              <View style={styles.bond}>
                <Text style={styles.bondTitle}>安稳纯债专区</Text>
                <Text style={styles.bondSubtitle}>
                  专注债券投资 追求稳健风格
                </Text>
                <BondChart />
                <Text style={styles.chartNote}>
                  固定演示数据 · 非实时行情 · 历史表现不代表未来收益
                </Text>
                <PrimaryButton
                  title="点击查看"
                  onPress={() =>
                    actions.open({
                      kind: 'products',
                      title: '安稳纯债专区',
                      category: 'fund',
                      requiresAuth: false,
                    })
                  }
                />
              </View>
            ) : (
              <StatePanel
                loading={products.loading && !products.data}
                error={products.error}
                onRetry={model.products.refresh}
              >
                {products.data?.items.map(p => (
                  <ProductCard
                    key={p.id}
                    product={p}
                    onPress={() =>
                      actions.open({
                        kind: 'product',
                        title: p.name,
                        id: p.id,
                        requiresAuth: false,
                      })
                    }
                  />
                ))}
              </StatePanel>
            )}
          </Surface>
          {data && (
            <CampaignCard
              {...data.banner}
              onPress={() => actions.open(data.banner.target)}
            />
          )}
          <DemoFootnote />
        </View>
      )}
    </ScrollView>
  );
}
function BondChart() {
  return (
    <Svg width="100%" height={82} viewBox="0 0 320 82">
      <Defs>
        <LinearGradient id="bar" x1="0" y1="0" x2="1" y2="0">
          <Stop offset="0" stopColor="#FF9879" />
          <Stop offset="1" stopColor="#F44B4C" />
        </LinearGradient>
      </Defs>
      {[
        { x: 45, h: 18, v: '2.67%', t: '近一年' },
        { x: 140, h: 31, v: '7.87%', t: '近三年' },
        { x: 235, h: 49, v: '13.64%', t: '近五年' },
      ].map(b => (
        <React.Fragment key={b.x}>
          <Rect
            x={b.x}
            y={64 - b.h}
            width={54}
            height={b.h}
            rx={9}
            fill="url(#bar)"
          />
          <SvgText
            x={b.x + 27}
            y={59 - b.h}
            textAnchor="middle"
            fill="#F04A3B"
            fontWeight="bold"
            fontSize={13}
          >
            {b.v}
          </SvgText>
          <SvgText
            x={b.x + 27}
            y={79}
            textAnchor="middle"
            fill="#866C55"
            fontSize={10}
          >
            {b.t}
          </SvgText>
        </React.Fragment>
      ))}
    </Svg>
  );
}
const styles = StyleSheet.create({
  marketCopy: { flex: 1 },
  page: { flex: 1, backgroundColor: '#F5F5F5' },
  scroll: { paddingBottom: 14 },
  header: { backgroundColor: '#FCF0E5', paddingBottom: 0 },
  body: { paddingHorizontal: 12, gap: 12 },
  market: {
    marginTop: 8,
    backgroundColor: '#FFF',
    borderRadius: 9,
    padding: 15,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  marketLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#E93D49',
    lineHeight: 21,
  },
  bond: { backgroundColor: '#FFF6F5', padding: 13, borderRadius: 13, gap: 2 },
  bondTitle: {
    fontSize: 20,
    fontWeight: '700',
    textAlign: 'center',
    color: '#222',
  },
  bondSubtitle: {
    fontSize: 15,
    textAlign: 'center',
    color: '#96918B',
    marginBottom: 4,
  },
  chartNote: {
    fontSize: 7,
    color: '#A29A92',
    textAlign: 'center',
    marginVertical: 3,
  },
});
