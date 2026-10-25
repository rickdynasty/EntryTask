/** EntryTask RN：通用服务与详情业务域模块：负责业务域对外公开接口，不跨域持有页面私有实现。 */
export type {
  Target,
  TargetKind,
  Service,
  Category,
  Campaign,
  ContentItem,
  Money,
  Asset,
  Page,
  Identity,
  Checkin,
} from '../domain/Content';
export type { PageActions, PageSession } from '../presentation/PageActions';
export type { Document, DocumentRow } from '../domain/Document';
export { projectDocument } from '../data/projectDocument';
export { DocumentViewModel } from '../presentation/DocumentViewModel';
export { DocumentScreen } from '../presentation/DocumentScreen';

export type { CheckinRepository } from '../domain/CheckinRepository';
export { createCheckinRepository } from '../data/createCheckinRepository';
