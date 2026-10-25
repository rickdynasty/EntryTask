/** Route绑定VM资源与活动生命周期，Screen保持纯展示。 */
import React from 'react';
import type { ProfileViewModel } from './ProfileViewModel';
import { ProfileScreen } from './ProfileScreen';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { useResource } from '@shared/presentation/useResource';
export function ProfileRoute({
  model,
  active,
  actions,
  session,
  pageState,
}: {
  model: ProfileViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number | undefined;
  pageState: PageUiStatePort;
}) {
  const identity = useResource(model.identity, active && session.authenticated);
  const assets = useResource(model.assets, active && session.authenticated);
  const financial = useResource(
    model.financial,
    active && session.authenticated,
  );
  return (
    <ProfileScreen
      identity={identity}
      assets={assets}
      financial={financial}
      active={active}
      actions={actions}
      session={session}
      pageState={pageState}
      retryIdentity={model.identity.refresh}
      retryAssets={model.assets.refresh}
      retryFinancial={model.financial.refresh}
    />
  );
}
