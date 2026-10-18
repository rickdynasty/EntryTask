/** EntryTask RN：详情页模型工厂：将受控业务目标映射到 API、Schema 和通用详情 ViewModel。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import * as generated from '@infra/network/generated/validators';
import { createId } from '@infra/logging/Logger';
import type { Target, Document } from '@features/services/public';
import { DocumentViewModel, projectDocument } from '@features/services/public';
import type { ResourceSource } from '@shared/domain/Resource';
import type { LoggerPort } from '@platform/ports/LoggerPort';
const apis: Partial<Record<Target['kind'], string>> = {
  products: 'product.list',
  product: 'product.detail',
  assets: 'asset.summary',
  accounts: 'account.list',
  account: 'account.detail',
  transactions: 'transaction.list',
  transaction: 'transaction.detail',
  articles: 'article.list',
  article: 'article.detail',
  activities: 'activity.list',
  activity: 'activity.detail',
  benefits: 'benefit.list',
  benefit: 'benefit.detail',
  claims: 'claim.list',
  claim: 'claim.detail',
  energy: 'energy.records',
  checkin: 'checkin.status',
  profile: 'profile.detail',
  items: 'personal.items',
  item: 'personal.itemDetail',
  services: 'service.catalog',
  search: 'search.query',
  messages: 'message.list',
  message: 'message.detail',
};
const validators: Record<string, (value: unknown) => boolean> = generated;
export function createDocumentModel(
  client: ResourceClient,
  target: Target,
  epoch: number,
  logger: LoggerPort,
): DocumentViewModel {
  // 目标只在静态 API 映射表中解析，写操作按业务类型附加响应校验与幂等键。
  const apiId = apis[target.kind];
  if (!apiId) throw new Error('UNKNOWN_ROUTE');
  const validate = validators[apiId.replaceAll('.', '_')];
  if (!validate) throw new Error('MISSING_SCHEMA');
  const idempotencyKey = createId();
  const load = (
    page: number,
    keyword: string,
    scope: string,
  ): ResourceSource<Document> => {
    const source = client.source<Record<string, unknown>>(
      {
        apiId,
        params: {
          page,
          pageSize: 10,
          ...(target.category
            ? { category: target.category, type: target.category }
            : {}),
          ...(target.id ? { id: target.id } : {}),
          ...(target.kind === 'search' ? { keyword, scope } : {}),
        },
      },
      validate,
      epoch,
    );
    return {
      open() {
        const resource = source.open();
        let previous: Record<string, unknown> | null = null;
        let projection: Document | null = null;
        return {
          ...resource,
          snapshot() {
            const state = resource.snapshot();
            if (previous !== state.data) {
              previous = state.data;
              projection = state.data
                ? projectDocument(target, state.data)
                : null;
            }
            return { ...state, data: projection };
          },
        };
      },
    };
  };
  const write =
    target.kind === 'checkin' || target.kind === 'benefit'
      ? async (date: string, signal: AbortSignal) => {
          const mutation =
            target.kind === 'checkin' ? 'checkin.submit' : 'benefit.claim';
          const guard = validators[mutation.replaceAll('.', '_')];
          if (!guard) throw new Error('MISSING_SCHEMA');
          await client.mutate(
            {
              apiId: mutation,
              ...(target.id ? { params: { id: target.id } } : {}),
              body: {
                idempotencyKey,
                ...(target.kind === 'checkin'
                  ? { expectedBusinessDate: date }
                  : {}),
              },
            },
            guard,
            signal,
          );
        }
      : undefined;
  return new DocumentViewModel(load, write, logger, target.kind);
}
