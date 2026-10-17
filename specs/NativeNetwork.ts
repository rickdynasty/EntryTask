import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  execute(requestJson: string): Promise<string>;
  cancel(requestId: string): void;
}

export default TurboModuleRegistry.get<Spec>('NativeNetwork');
