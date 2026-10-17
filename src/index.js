/** Single registration for both CLI and native-hosted entry points. */
import { AppRegistry } from 'react-native';
import App from './app/App';
import { name as appName } from '../app.json';

AppRegistry.registerComponent(appName, () => App);
