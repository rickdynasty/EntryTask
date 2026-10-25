/** Route绑定VM资源与活动生命周期，Screen保持纯展示。 */
import React, { useEffect, useSyncExternalStore } from 'react';
import type { MemberViewModel } from './MemberViewModel';
import { MemberScreen } from './MemberScreen';
import type { PageActions, PageSession } from '@features/services/public';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { useResource } from '@shared/presentation/useResource';
export function MemberRoute({
  model,
  active,
  actions,
  session,
  pageState,
}: {
  model: MemberViewModel;
  active: boolean;
  actions: PageActions;
  session: PageSession;
  unread: number | undefined;
  pageState: PageUiStatePort;
}) {
  const content = useResource(model.content, active);
  const summary = useResource(model.summary, active && session.authenticated);
  const checkin = useResource(model.checkin, active && session.authenticated);
  const operation = useSyncExternalStore(
    model.subscribeOperation,
    model.getOperation,
  );
  useEffect(() => () => model.deactivateSubmission(), [model]);
  return (
    <MemberScreen
      content={content}
      summary={summary}
      checkin={checkin}
      operation={operation}
      active={active}
      actions={actions}
      session={session}
      pageState={pageState}
      retryContent={model.content.refresh}
      retrySummary={model.summary.refresh}
      retryCheckin={model.checkin.refresh}
      onSubmit={() => {
        void model.submit();
      }}
    />
  );
}
