/** EntryTask RN：React Native Codegen TurboModule 契约：只定义 RN 与 Native 之间的传输签名。 */
import type { TurboModule, CodegenTypes } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
export interface Spec extends TurboModule {
  /** 上报运行协议与流程编号，取得宿主能力和当前 Shell 快照。 */
  bootstrap(requestJson: string): Promise<string>;
  /** 请求 Native 执行受控导航、认证或会话命令。 */
  command(requestJson: string): Promise<string>;
  /** 接收 Native 根 Tab、登录态及页面深度变化。 */
  readonly onHostEvent: CodegenTypes.EventEmitter<string>;
}
export default TurboModuleRegistry.get<Spec>('NativeHost');
