/** EntryTask RN：React Native Codegen TurboModule 契约：只定义 RN 与 Native 之间的传输签名。 */
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /** 批量接收带 requestId/flowId 的安全结构化日志事件。 */
  emitBatch(batchJson: string): Promise<string>;
}

export default TurboModuleRegistry.get<Spec>('NativeTelemetry');
