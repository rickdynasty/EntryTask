/** EntryTask RN：会员业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React, { useState } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { MemberViewModel } from './MemberViewModel';
import type {
  PageActions,
  PageSession,
  ContentItem,
} from '@features/services/public';
import { useResource } from '@shared/presentation/useResource';
import { ChannelHeader } from '@shared/ui/ChannelHeader';
import {
  Artwork,
  ChoiceRail,
  DemoFootnote,
  PrimaryButton,
  SectionHeading,
  StatePanel,
  Surface,
  ui,
} from '@shared/ui/Primitives';
import { CampaignCard } from '@shared/ui/CampaignCard';
/** 分开管理会员页面本地选择与 Native 返回的业务事实。 */
export function MemberScreen({
  model,
  active,
  actions,
  session,
  unread,
}: {
  model: MemberViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number;
}) {
  // 分段和展示区域为本地 UI 状态；身份、签到、权益事实始终来自 Native 查询。
  const [segment, setSegment] = useState('会员');
  const [zone, setZone] = useState('exclusive');
  const content = useResource(model.content, active);
  const summary = useResource(model.summary, active && session.authenticated);
  const checkin = useResource(model.checkin, active && session.authenticated);
  const activities = useResource(
    model.activities,
    active && segment === '活动',
  );
  const claims = useResource(
    model.claims,
    active && session.authenticated && segment === '已领权益',
  );
  const data = content.data;
  const openBenefit = (item: ContentItem) =>
    actions.open({
      kind: 'benefit',
      title: item.title,
      id: item.id,
      requiresAuth: false,
    });
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
            if (session.authenticated) {
              model.summary.refresh();
              model.checkin.refresh();
            }
          }}
        />
      }
    >
      <View style={styles.header}>
        <ChannelHeader
          mode="channel"
          actions={actions.header}
          tabs={['会员', '活动', '已领权益']}
          selected={segment}
          onSelect={value => {
            actions.track('member', 'segment.selected', value);
            if (value === '已领权益' && !session.authenticated) {
              actions.open({
                kind: 'claims',
                title: '已领权益',
                requiresAuth: true,
              });
            } else setSegment(value);
          }}
          unread={unread}
        />
      </View>
      <View style={styles.body}>
        <StatePanel
          loading={content.loading && !data}
          error={content.error}
          onRetry={model.content.refresh}
        />
        {segment === '活动' ? (
          <StatePanel
            loading={activities.loading}
            error={activities.error}
            onRetry={model.activities.refresh}
          >
            {activities.data?.items.map(item => (
              <CampaignCard
                key={item.id}
                title={item.title}
                subtitle={item.summary ?? ''}
                assetKey={item.assetKey ?? ''}
                onPress={() =>
                  actions.open({
                    kind: 'activity',
                    title: item.title,
                    id: item.id,
                    requiresAuth: false,
                  })
                }
              />
            ))}
          </StatePanel>
        ) : segment === '已领权益' ? (
          <StatePanel
            loading={claims.loading}
            error={claims.error}
            empty={claims.data?.items.length === 0}
            onRetry={model.claims.refresh}
          >
            {claims.data?.items.map(item => (
              <Surface key={item.id}>
                <Text style={ui.heading}>{item.title}</Text>
                <Text style={ui.muted}>已领取 · {item.date}</Text>
              </Surface>
            ))}
          </StatePanel>
        ) : (
          <>
            <Surface style={styles.welcome}>
              <Text style={styles.welcomeTitle}>
                {session.authenticated
                  ? `${summary.data?.nickname ?? '会员'}，欢迎回来`
                  : data?.welcome ?? '欢迎来到会员频道'}
              </Text>
              <Text style={styles.welcomeSubtitle}>
                {session.authenticated
                  ? `演示等级 Lv.${summary.data?.level ?? '—'} · 能量值 ${
                      summary.data?.energy ?? '—'
                    }`
                  : data?.welcomeSubtitle ?? '登录后享受更多权益和活动'}
              </Text>
              <PrimaryButton
                title={session.authenticated ? '查看会员权益' : '点击登录'}
                onPress={() =>
                  session.authenticated
                    ? actions.open({
                        kind: 'benefits',
                        title: '会员权益',
                        requiresAuth: false,
                      })
                    : actions.login()
                }
              />
            </Surface>
            <Surface style={styles.exclusive}>
              <ChoiceRail
                segmented
                items={[
                  { id: 'exclusive', title: '会员专属' },
                  { id: 'energy', title: '能量值乐园' },
                ]}
                selected={zone}
                onChange={value => {
                  actions.track('member', 'zone.selected', value);
                  setZone(value);
                }}
              />
              {zone === 'energy' ? (
                <View style={styles.energy}>
                  <Text style={ui.heading}>能量值乐园</Text>
                  <Text style={styles.energyValue}>
                    {session.authenticated ? summary.data?.energy ?? '—' : '--'}
                  </Text>
                  <Text style={ui.muted}>
                    本原型仅在连续签到第3/7天奖励30/100能量值
                  </Text>
                  <PrimaryButton
                    title="查看能量记录"
                    onPress={() =>
                      actions.open({
                        kind: 'energy',
                        title: '能量值记录',
                        requiresAuth: true,
                      })
                    }
                  />
                </View>
              ) : (
                <>
                  <SectionHeading title="会员专属活动" />
                  <View style={styles.checkin}>
                    <View style={ui.between}>
                      <Text style={styles.cardTitle}>
                        {data?.checkinTitle ?? '连续签到赢好礼'}
                      </Text>
                      <PrimaryButton
                        small
                        title={
                          checkin.data?.checkedToday ? '今日已签到' : '立即签到'
                        }
                        onPress={() =>
                          actions.open({
                            kind: 'checkin',
                            title: '连续签到',
                            requiresAuth: true,
                          })
                        }
                      />
                    </View>
                    <View style={[ui.row, styles.rewardsRow]}>
                      {[
                        { days: 3, value: 30 },
                        { days: 7, value: 100 },
                      ].map(item => (
                        <View key={item.days} style={styles.reward}>
                          <View>
                            <Text style={styles.rewardTitle}>
                              {item.value}能量值
                            </Text>
                            <Text style={styles.rewardSubtitle}>
                              连续{item.days}日
                            </Text>
                          </View>
                          <View style={styles.star}>
                            <Text style={styles.starText}>
                              R{'\n'}×{item.value}
                            </Text>
                          </View>
                        </View>
                      ))}
                    </View>
                  </View>
                  <View style={[ui.row, styles.benefitColumns]}>
                    <View style={styles.cardDesign}>
                      <Text style={styles.cardTitle}>
                        {data?.cardTitle ?? '定制你的专属卡号'}
                      </Text>
                      <Text style={ui.muted}>{data?.cardSubtitle}</Text>
                      <Artwork name="custom-cards" style={styles.cardsArt} />
                      <PrimaryButton
                        title="立即申卡"
                        onPress={() =>
                          actions.open({
                            kind: 'demo',
                            title: '信用卡申请',
                            requiresAuth: true,
                          })
                        }
                      />
                    </View>
                    <View style={styles.sideCards}>
                      {data?.benefits.map(item => (
                        <Pressable
                          key={item.id}
                          onPress={() => openBenefit(item)}
                          style={[
                            styles.benefit,
                            item.tone === 'blue' ? styles.blue : styles.gold,
                          ]}
                        >
                          <Text style={styles.cardTitle}>{item.title}</Text>
                          <Text style={ui.muted}>{item.subtitle}</Text>
                          <Artwork
                            name={item.assetKey ?? ''}
                            style={styles.benefitArt}
                          />
                          <Text style={styles.see}>去看看 →</Text>
                        </Pressable>
                      ))}
                    </View>
                  </View>
                </>
              )}
            </Surface>
            <DemoFootnote text="演示会员权益 · 不兑换真实权益或实物" />
          </>
        )}
      </View>
    </ScrollView>
  );
}
const styles = StyleSheet.create({
  blue: { backgroundColor: '#F2F5FD' },
  gold: { backgroundColor: '#FFF7F0' },
  rewardsRow: { gap: 10, marginTop: 15 },
  benefitColumns: { gap: 12, alignItems: 'stretch' },
  page: { flex: 1, backgroundColor: '#F5F5F5' },
  scroll: { paddingBottom: 12 },
  header: { backgroundColor: '#FCF0E5' },
  body: { paddingHorizontal: 12, gap: 14 },
  welcome: {
    marginTop: 8,
    paddingHorizontal: 25,
    paddingVertical: 30,
    gap: 14,
  },
  welcomeTitle: {
    fontSize: 18,
    fontWeight: '700',
    textAlign: 'center',
    color: '#161616',
  },
  welcomeSubtitle: {
    fontSize: 15,
    textAlign: 'center',
    color: '#D78984',
    marginBottom: 17,
  },
  exclusive: { gap: 18, paddingHorizontal: 12, paddingTop: 12 },
  checkin: { padding: 13, backgroundColor: '#FFF8F3', borderRadius: 6 },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#292623',
    lineHeight: 23,
  },
  reward: {
    flex: 1,
    backgroundColor: '#FFF0DF',
    borderRadius: 3,
    padding: 9,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  rewardTitle: { fontSize: 13, color: '#9C763F' },
  rewardSubtitle: { fontSize: 11, color: '#9C763F', marginTop: 5 },
  star: {
    width: 28,
    height: 32,
    backgroundColor: '#FFD983',
    borderRadius: 12,
    transform: [{ rotate: '10deg' }],
    alignItems: 'center',
    justifyContent: 'center',
  },
  starText: {
    fontSize: 10,
    color: '#F04D3C',
    fontWeight: '700',
    textAlign: 'center',
  },
  cardDesign: {
    flex: 1,
    backgroundColor: '#FFF5F3',
    borderRadius: 5,
    padding: 12,
    gap: 6,
    justifyContent: 'space-between',
  },
  cardsArt: { height: 98, width: '100%', marginVertical: 8 },
  sideCards: { flex: 1, gap: 12 },
  benefit: { minHeight: 110, borderRadius: 5, padding: 13, overflow: 'hidden' },
  benefitArt: {
    position: 'absolute',
    bottom: 10,
    right: 6,
    width: 55,
    height: 50,
  },
  see: { marginTop: 25, color: '#9A783F', fontSize: 13 },
  energy: { padding: 20, gap: 16 },
  energyValue: { fontSize: 40, fontWeight: '600', color: '#BC8D58' },
});
