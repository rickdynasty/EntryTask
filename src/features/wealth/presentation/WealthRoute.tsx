/** Route绑定VM资源与活动生命周期，Screen保持纯展示。 */
import React, { useSyncExternalStore } from 'react';
import type { WealthViewModel } from './WealthViewModel';
import { WealthScreen } from './WealthScreen';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { useResource } from '@shared/presentation/useResource';
export function WealthRoute({
  model,
  active,
  actions,
  session,
  pageState,
}: {
  model: WealthViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number | undefined;
  pageState: PageUiStatePort;
}) {
  const selection = useSyncExternalStore(
    model.ui.subscribe,
    model.ui.getSnapshot,
  );
  const content = useResource(model.content, active);
  const products = useResource(model.products, active);
  const assets = useResource(model.assets, active && session.authenticated);
  return (
    <WealthScreen
      content={content}
      assets={assets}
      products={products}
      category={selection.category}
      active={active}
      actions={actions}
      session={session}
      pageState={pageState}
      retryContent={model.content.refresh}
      retryAssets={model.assets.refresh}
      retryProducts={model.products.refresh}
      onCategory={id => model.selectCategory(id)}
    />
  );
}
