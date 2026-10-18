/** EntryTask RN：财富业务域模块：负责业务域对外公开接口，不跨域持有页面私有实现。 */
export { createWealthViewModel } from '../di/createWealthViewModel';
export type { WealthViewModel } from '../presentation/WealthViewModel';
export type { Product, WealthContent } from '../domain/Wealth';
export { ProductCard } from '../presentation/ProductCard';
export { AssetSummary } from '../presentation/AssetSummary';
export { WealthScreen } from '../presentation/WealthScreen';
