import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  read(requestJson: string): Promise<string>;
  write(requestJson: string): Promise<string>;
  remove(requestJson: string): Promise<string>;
}

export default TurboModuleRegistry.get<Spec>('NativeStorage');
