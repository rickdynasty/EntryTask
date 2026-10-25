/** 登录会员根页：VIP、权益与真实签到闭环；离页取消未完成动作。 */
import React from 'react';
import { Text, View, StyleSheet } from 'react-native';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import { StatePanel, Surface, ui } from '@shared/ui/Primitives';
import { PageFrame } from '@shared/ui/DashboardComponents';
import {
  VipProfileCard,
  InteractiveTaskCard,
} from './components/MemberRegions';
import type { ResourceState } from '@shared/domain/Resource';
import type { MemberContent } from '../domain/Member';
import type { Identity, Checkin } from '@features/services/public';
export function MemberScreen({
  content,
  summary,
  checkin,
  operation,
  active,
  actions,
  session,
  pageState,
  retryContent,
  retrySummary,
  retryCheckin,
  onSubmit,
}: {
  active: boolean;
  actions: PageActions;
  pageState: PageUiStatePort;
  session: PageSession;
  content: ResourceState<MemberContent>;
  summary: ResourceState<Identity>;
  checkin: ResourceState<Checkin>;
  operation: { submitting: boolean; message: string };
  retryContent: () => void;
  retrySummary: () => void;
  retryCheckin: () => void;
  onSubmit: () => void;
}) {
  return (
    <PageFrame
      ready={!!summary.data && !!checkin.data}
      state={pageState}
      active={active}
      regions={[
        {
          id: 'member-vip',
          content: (
            <>
              <Text style={ui.heading}>会员</Text>
              <View style={styles.vipShell}>
                <VipProfileCard
                  identity={summary.data}
                  visible={session.assetsVisible}
                  onBenefits={() =>
                    actions.open({
                      kind: 'demo',
                      title: '全部权益',
                      requiresAuth: true,
                    })
                  }
                />
                <StatePanel
                  loading={summary.loading && !summary.data}
                  error={summary.error}
                  onRetry={retrySummary}
                />
                <Surface style={styles.benefits}>
                  <StatePanel
                    loading={content.loading && !content.data}
                    error={content.error}
                    onRetry={retryContent}
                  />
                  {content.data && (
                    <ServiceGrid
                      columns={4}
                      tone="blue"
                      items={content.data.benefitEntries}
                      onSelect={item => actions.open(item.target)}
                    />
                  )}
                </Surface>
              </View>
            </>
          ),
        },
        {
          id: 'member-task',
          content: (
            <>
              <Text style={ui.heading}>会员专属活动</Text>
              <InteractiveTaskCard
                data={checkin.data}
                operation={operation}
                refreshing={checkin.loading}
                onSubmit={() => {
                  actions.track('member', 'checkin.submit', 'task');
                  onSubmit();
                }}
              />
              <StatePanel
                loading={checkin.loading && !checkin.data}
                error={checkin.error}
                onRetry={retryCheckin}
              />
            </>
          ),
        },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  vipShell: { backgroundColor: '#159BC5', borderRadius: 8, paddingBottom: 12 },
  benefits: { marginHorizontal: 12 },
});
