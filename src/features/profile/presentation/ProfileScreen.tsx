/** 我的根页：身份统计及四张独立财务卡，移除非必做服务聚合。 */
import React from 'react';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { StatePanel } from '@shared/ui/Primitives';
import { PageFrame, AssetPrivacyCard } from '@shared/ui/DashboardComponents';
import {
  UserCardHeader,
  MonthlyCashflowCard,
  CreditBillCard,
  LoanSummaryCard,
} from './components/ProfileRegions';
import type { ResourceState } from '@shared/domain/Resource';
import type { Identity, Asset } from '@features/services/public';
import type { FinancialOverview } from '../domain/Profile';
export function ProfileScreen({
  identity,
  assets,
  financial,
  active,
  actions,
  session,
  pageState,
  retryIdentity,
  retryAssets,
  retryFinancial,
}: {
  active: boolean;
  actions: PageActions;
  pageState: PageUiStatePort;
  session: PageSession;
  identity: ResourceState<Identity>;
  assets: ResourceState<Asset>;
  financial: ResourceState<FinancialOverview>;
  retryIdentity: () => void;
  retryAssets: () => void;
  retryFinancial: () => void;
}) {
  const demo = (title: string) =>
    actions.open({ kind: 'demo', title, requiresAuth: true });
  return (
    <PageFrame
      ready={!!identity.data && !!financial.data}
      state={pageState}
      active={active}
      regions={[
        {
          id: 'profile-identity',
          content: (
            <>
              <UserCardHeader data={identity.data} onStat={demo} />
              <StatePanel
                loading={identity.loading && !identity.data}
                error={identity.error}
                onRetry={retryIdentity}
              />
            </>
          ),
        },
        {
          id: 'profile-assets',
          content: (
            <>
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
          id: 'profile-cashflow',
          content: (
            <>
              <MonthlyCashflowCard
                data={financial.data}
                visible={session.assetsVisible}
              />
              <StatePanel
                loading={financial.loading && !financial.data}
                error={financial.error}
                onRetry={retryFinancial}
              />
            </>
          ),
        },
        {
          id: 'profile-credit',
          content: (
            <CreditBillCard
              data={financial.data}
              visible={session.assetsVisible}
              onOpen={() => demo('信用卡服务')}
            />
          ),
        },
        {
          id: 'profile-loan',
          content: (
            <LoanSummaryCard
              data={financial.data}
              visible={session.assetsVisible}
              onOpen={() => demo('借款服务')}
            />
          ),
        },
      ]}
    />
  );
}
