/** Route绑定VM资源与活动生命周期，Screen保持纯展示。 */
import React, { useSyncExternalStore } from 'react';
import type { HomePageViewModel } from './HomePageViewModel';
import { HomeScreen } from './HomeScreen';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { useResource } from '@shared/presentation/useResource';
export function HomeRoute({
  model,
  active,
  actions,
  unread,
  pageState,
  notificationError,
  onRetryNotifications,
}: {
  model: HomePageViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number | undefined;
  pageState: PageUiStatePort;
  notificationError: string | null;
  onRetryNotifications: () => void;
}) {
  const selection = useSyncExternalStore(
    model.ui.subscribe,
    model.ui.getSnapshot,
  );
  const content = useResource(model.content, active);
  const products = useResource(model.products, active);
  return (
    <HomeScreen
      content={content}
      products={products}
      category={selection.category}
      onCategory={id => model.selectCategory(id)}
      retryProducts={model.products.refresh}
      notificationError={notificationError}
      onRetryNotifications={onRetryNotifications}
      active={active}
      actions={actions}
      unread={unread}
      pageState={pageState}
      retry={model.content.refresh}
    />
  );
}
