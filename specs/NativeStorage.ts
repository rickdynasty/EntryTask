/** EntryTask RN：React Native Codegen TurboModule 契约：只定义 RN 与 Native 之间的传输签名。 */
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /** 按受控键读取当前用户作用域中的序列化值。 */
  read(requestJson: string): Promise<string>;
  /** 写入经 Native 校验的类型化用户偏好。 */
  write(requestJson: string): Promise<string>;
  /** 删除指定用户作用域下的非凭证偏好。 */
  remove(requestJson: string): Promise<string>;
}

export default TurboModuleRegistry.get<Spec>('NativeStorage');
