/** EntryTask RN：消息业务域模块：负责数据映射与访问实现，不跨域持有页面私有实现。 */
import type { ResourceClient } from '@infra/state/ResourceClient';
import {
  message_unread,
  message_read,
} from '@infra/network/generated/validators';
import { ResourceViewModel } from '@shared/presentation/ResourceViewModel';
import type { LoggerPort } from '@platform/ports/LoggerPort';
import { normalizeError } from '@shared/domain/AppError';
export function createMessaging(
  client: ResourceClient,
  epoch: number,
  logger: LoggerPort,
) {
  // 消息未读数是查询资源；已读通过 Native 写入并依赖服务端状态确认结果。
  const unread = new ResourceViewModel(
    client.source<{ unreadCount: number }>(
      { apiId: 'message.unread' },
      message_unread,
      epoch,
    ),
  );
  return {
    unread,
    async read(id: string, key: string, signal: AbortSignal) {
      try {
        await client.mutate(
          {
            apiId: 'message.read',
            params: { id },
            body: { idempotencyKey: key },
          },
          message_read,
          signal,
        );
        logger.log('info', 'messaging', 'message.read.persisted', {
          pageId: 'message',
        });
      } catch (error) {
        // 消息 ID、幂等键和原始错误不写日志；由上层按错误码排查。
        const safe = normalizeError(error);
        if (safe.kind !== 'cancelled')
          logger.log('warn', 'messaging', 'message.read.persist_failed', {
            code: safe.code,
            pageId: 'message',
          });
        throw error;
      }
    },
    dispose() {
      unread.deactivate();
    },
  };
}
