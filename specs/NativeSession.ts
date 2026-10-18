/** EntryTask RN：React Native Codegen TurboModule 契约：只定义 RN 与 Native 之间的传输签名。 */
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /** 读取认证状态和会话 epoch；RN 不接触凭证。 */
  getSnapshot(requestJson: string): Promise<string>;
}

export default TurboModuleRegistry.get<Spec>('NativeSession');
