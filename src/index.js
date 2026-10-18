/** EntryTask RN：RN 官方入口：注册 EntryTask 根组件并交由 Native ReactHost 挂载。 */
/** CLI 与 Native 宿主共用此入口，仅注册一次根组件。 */
import { AppRegistry } from 'react-native';
import App from './app/App';
import { name as appName } from '../app.json';

AppRegistry.registerComponent(appName, () => App);
