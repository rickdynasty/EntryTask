/** EntryTask RN：本地原型素材注册：以白名单名称映射打包图片资源。 */
import type { ImageSourcePropType } from 'react-native';
const artwork: Record<string, ImageSourcePropType> = {
  'hero-ginkgo': require('./hero-ginkgo.png'),
  'certification-gift': require('./certification-gift.png'),
  'seasonal-gifts': require('./seasonal-gifts.png'),
  'custom-cards': require('./custom-cards.png'),
  crown: require('./crown.png'),
  book: require('./book.png'),
  'profile-avatar': require('./profile-avatar.png'),
  'advisor-avatar': require('./advisor-avatar.png'),
  'credit-gift': require('./credit-gift.png'),
  'quick-0': require('./quick-0.png'),
  'quick-1': require('./quick-1.png'),
  'quick-2': require('./quick-2.png'),
  'quick-3': require('./quick-3.png'),
};
export const assetFor = (key: string): ImageSourcePropType | undefined =>
  artwork[key];
