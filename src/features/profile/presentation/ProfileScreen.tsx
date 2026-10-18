/** EntryTask RN：我的业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { ProfileViewModel } from './ProfileViewModel';
import type {
  PageActions,
  PageSession,
  Target,
} from '@features/services/public';
import { AssetSummary } from '@features/wealth/public';
import { useResource } from '@shared/presentation/useResource';
import { ChannelHeader } from '@shared/ui/ChannelHeader';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import { Icon } from '@shared/ui/Icon';
import {
  Artwork,
  DemoFootnote,
  PrimaryButton,
  SectionHeading,
  StatePanel,
  Surface,
  ui,
} from '@shared/ui/Primitives';
import { formatMinor, formatRatio } from '@shared/formatting/numbers';
/** 游客只加载公开营销内容，登录后订阅身份与资产数据。 */
export function ProfileScreen({
  model,
  active,
  actions,
  session,
  unread,
}: {
  model: ProfileViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number;
}) {
  // 个人信息仅在 Native 会话认证后订阅；游客页继续展示公开营销内容。
  const marketing = useResource(model.content, active);
  const identity = useResource(model.identity, active && session.authenticated);
  const assets = useResource(model.assets, active && session.authenticated);
  const info = identity.data;
  const data = marketing.data;
  const stats: { title: string; value: number | undefined; target: Target }[] =
    [
      {
        title: '银行卡',
        value: info?.bankCards,
        target: { kind: 'accounts', title: '我的银行卡', requiresAuth: true },
      },
      {
        title: '待办',
        value: info?.todos,
        target: {
          kind: 'items',
          title: '我的待办',
          category: 'todo',
          requiresAuth: true,
        },
      },
      {
        title: '卡券',
        value: info?.coupons,
        target: { kind: 'claims', title: '我的卡券', requiresAuth: true },
      },
      {
        title: '积分',
        value: info?.points,
        target: {
          kind: 'items',
          title: '我的积分',
          category: 'points',
          requiresAuth: true,
        },
      },
      {
        title: '能量值',
        value: info?.energy,
        target: { kind: 'energy', title: '能量值记录', requiresAuth: true },
      },
    ];
  return (
    <ScrollView
      style={styles.page}
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.scroll}
      refreshControl={
        <RefreshControl
          refreshing={marketing.loading && !!data}
          onRefresh={() => {
            model.content.refresh();
            if (session.authenticated) {
              model.identity.refresh();
              model.assets.refresh();
            }
          }}
        />
      }
    >
      <View style={styles.header}>
        <ChannelHeader
          mode="profile"
          actions={actions.header}
          authenticated={session.authenticated}
          unread={unread}
        />
        <Surface style={styles.identity}>
          <View style={styles.welcomeStrip}>
            <Text style={styles.welcomeText}>中信银行欢迎您</Text>
          </View>
          <Pressable
            accessibilityLabel={
              session.authenticated ? '查看个人资料' : '登录查看个人资料'
            }
            onPress={() =>
              actions.open({
                kind: 'profile',
                title: '个人资料',
                requiresAuth: true,
              })
            }
            style={[ui.row, styles.identityRow]}
          >
            <Artwork name="profile-avatar" style={styles.avatar} />
            <View style={styles.identityCopy}>
              <Text style={styles.phone}>
                {session.authenticated
                  ? info?.phoneMasked ?? '正在加载资料'
                  : '登录享更多服务'}
              </Text>
              <Text style={styles.green}>▧ 低碳</Text>
            </View>
          </Pressable>
          <View style={styles.stats}>
            {stats.map(item => (
              <Pressable
                key={item.title}
                onPress={() => actions.open(item.target)}
                style={styles.stat}
              >
                <Text style={styles.statValue}>
                  {session.authenticated ? item.value ?? '—' : '--'}
                </Text>
                <Text style={styles.statLabel}>{item.title}</Text>
              </Pressable>
            ))}
          </View>
        </Surface>
      </View>
      <View style={styles.body}>
        {data && (
          <ServiceGrid
            items={data.services}
            onSelect={item => actions.open(item.target)}
          />
        )}
        <StatePanel
          loading={marketing.loading && !data}
          error={marketing.error}
          onRetry={model.content.refresh}
        />
        <Surface style={styles.assetCard}>
          <SectionHeading
            title="资产总览"
            onMore={() =>
              actions.open({
                kind: 'assets',
                title: '资产总览',
                requiresAuth: true,
              })
            }
          />
          <View style={ui.between}>
            <View style={styles.assetColumn}>
              <AssetSummary
                compact
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
            </View>
            {!session.authenticated && (
              <PrimaryButton small title="立即登录" onPress={actions.login} />
            )}
          </View>
          <Pressable
            onPress={() =>
              actions.open({
                kind: 'assets',
                title: '资产总览',
                requiresAuth: true,
              })
            }
            style={styles.slogan}
          >
            <Text style={ui.body}>
              {data?.assetSlogan ?? '做自己人生的CEO'}
            </Text>
            <Icon name="chevron" size={16} color="#999" />
          </Pressable>
          <Pressable
            onPress={() =>
              actions.open({
                kind: 'activities',
                title: '金秋精选活动',
                requiresAuth: false,
              })
            }
            style={[ui.row, styles.noticeRow]}
          >
            <Icon name="volume" size={16} color="#AAA" />
            <Text numberOfLines={1} style={[ui.muted, styles.noticeCopy]}>
              {data?.assetNotice}
            </Text>
            <Icon name="chevron" size={16} color="#AAA" />
          </Pressable>
        </Surface>
        <Pressable
          onPress={() =>
            actions.open({
              kind: 'demo',
              title: '信用卡服务',
              requiresAuth: true,
            })
          }
        >
          <Surface style={styles.credit}>
            <View>
              <Text style={ui.heading}>{data?.creditTitle ?? '信用卡'}</Text>
              <Text style={styles.creditTitle}>
                {session.authenticated ? '查看信用卡服务' : '登录享更多服务'}
              </Text>
              <Text style={ui.muted}>{data?.creditSubtitle}</Text>
            </View>
            <Artwork name="credit-gift" style={styles.creditArt} />
          </Surface>
        </Pressable>
        <Surface>
          <Text style={ui.heading}>{data?.loanTitle ?? '我的借钱'}</Text>
          <View style={[ui.row, styles.loanMetrics]}>
            <View>
              <Text style={styles.limit}>
                ¥{data ? formatMinor(data.loanLimit.amountMinor) : '—'}
              </Text>
              <Text style={ui.muted}>最高可借额度（示例）</Text>
            </View>
            <View>
              <Text style={styles.limit}>
                {data ? formatRatio(data.loanRate) : '—'}
              </Text>
              <Text style={ui.muted}>年利率低至（示例）</Text>
            </View>
            <Pressable
              onPress={() =>
                actions.open({
                  kind: 'demo',
                  title: '借款服务',
                  requiresAuth: true,
                })
              }
              style={styles.borrow}
            >
              <Text style={styles.borrowText}>去借款</Text>
            </Pressable>
          </View>
        </Surface>
        <DemoFootnote text="模拟资产与营销示例 · 不代表个人授信或真实金融服务" />
      </View>
    </ScrollView>
  );
}
const styles = StyleSheet.create({
  assetCard: { gap: 8 },
  identityRow: { gap: 14, marginVertical: 4 },
  assetColumn: { flex: 1 },
  noticeRow: { gap: 9, paddingVertical: 4 },
  noticeCopy: { flex: 1 },
  loanMetrics: { gap: 22 },
  page: { flex: 1, backgroundColor: '#F5F5F5' },
  scroll: { paddingBottom: 12 },
  header: { backgroundColor: '#FCF0E5' },
  identity: {
    marginHorizontal: 12,
    padding: 10,
    gap: 6,
    borderBottomLeftRadius: 0,
    borderBottomRightRadius: 0,
    overflow: 'hidden',
  },
  welcomeStrip: {
    position: 'absolute',
    top: 3,
    right: 0,
    backgroundColor: '#A7B5D2',
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderBottomLeftRadius: 18,
  },
  welcomeText: { color: '#FFF', fontSize: 11 },
  avatar: { width: 57, height: 57 },
  identityCopy: { paddingTop: 9 },
  phone: { fontSize: 19, fontWeight: '600', color: '#242321' },
  green: {
    fontSize: 11,
    color: '#67B99C',
    backgroundColor: '#E5F7EF',
    alignSelf: 'flex-start',
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 3,
    marginTop: 7,
  },
  stats: { flexDirection: 'row', paddingTop: 0, paddingBottom: 0 },
  stat: { flex: 1, alignItems: 'center', gap: 6, paddingVertical: 5 },
  statValue: { fontSize: 21, color: '#242321' },
  statLabel: { fontSize: 13, color: '#44413C' },
  body: { paddingHorizontal: 12, gap: 12 },
  slogan: {
    backgroundColor: '#FFF8F2',
    borderColor: '#F3EAE3',
    borderWidth: 1,
    borderRadius: 4,
    padding: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  credit: { minHeight: 112, justifyContent: 'center' },
  creditTitle: { fontSize: 18, color: '#333', marginTop: 14, marginBottom: 3 },
  creditArt: {
    position: 'absolute',
    right: 21,
    top: 16,
    width: 70,
    height: 76,
  },
  limit: { fontSize: 21, color: '#202020', marginBottom: 5 },
  borrow: {
    marginLeft: 'auto',
    backgroundColor: '#FAF4F0',
    padding: 8,
    borderRadius: 3,
  },
  borrowText: { fontSize: 13, color: '#BF8D68' },
});
