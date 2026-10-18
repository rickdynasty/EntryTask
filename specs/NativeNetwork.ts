/** EntryTask RN：React Native Codegen TurboModule 契约：只定义 RN 与 Native 之间的传输签名。 */
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /** 执行按 apiId 选路的 Native 网络请求并返回 JSON 信封。 */
  execute(requestJson: string): Promise<string>;
  /** 取消指定请求编号对应的在途请求。 */
  cancel(requestId: string): void;
}

export default TurboModuleRegistry.get<Spec>('NativeNetwork');
