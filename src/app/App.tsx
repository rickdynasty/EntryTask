/** EntryTask RN：应用根组件：创建单份应用依赖图，处理 Native 握手与启动状态。 */
import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Button,
  Platform,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { AppNavigator } from './navigation/AppNavigator';
import { createId } from '@infra/logging/Logger';
import { normalizeError } from '@shared/domain/AppError';
import { createApplication } from './CompositionRoot';

// The single registered component creates one application graph per mounted Surface.
// Android owns a single ReactHost; Native-only RootContentHostFragment doesn't mount this graph.
export default function App() {
  const [application] = useState(createApplication);
  const [phase, setPhase] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState({ message: '', code: '', flowId: '' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    // 每轮启动分配独立 flowId；卸载后的异步回调不再更新 React 状态。
    let active = true;
    const flowId = createId();
    void application
      .bootstrap(flowId)
      .then(() => {
        if (active) {
          setPhase('ready');
        }
      })
      .catch(reason => {
        // 先归一错误再展示，仅保留安全错误码和可用于日志检索的流程号。
        const safe = normalizeError(reason);
        application.logger.log('error', 'bootstrap', 'bootstrap.failed', {
          code: safe.code,
          flowId,
        });
        if (active) {
          setError({ message: safe.message, code: safe.code, flowId });
          setPhase('error');
        }
      });
    return () => {
      active = false;
    };
  }, [application, attempt]);
  useEffect(() => () => application.dispose(), [application]);
  // 启动失败保留错误编号和显式重试入口；正常阶段仅挂载一个导航树。
  return (
    <SafeAreaProvider>
      <SafeAreaView edges={['top', 'left', 'right']} style={styles.root}>
        {phase === 'ready' ? (
          <AppNavigator application={application} />
        ) : (
          <View style={styles.status}>
            {phase === 'loading' ? (
              <ActivityIndicator accessibilityLabel="正在准备应用" />
            ) : (
              <>
                <Text>{error.message}</Text>
                <Text selectable style={styles.diagnostic}>
                  错误码：{error.code}
                </Text>
                <Text selectable style={styles.diagnostic}>
                  诊断编号：{error.flowId}
                </Text>
                {__DEV__ && (
                  <Text style={styles.diagnostic}>
                    {Platform.OS === 'ios'
                      ? '查看 Xcode Console 或 RN DevTools Console'
                      : '查看终端 npm run logs:android 或 RN DevTools Console'}
                  </Text>
                )}
                <Button
                  title="重试初始化"
                  onPress={() => {
                    setPhase('loading');
                    setAttempt(value => value + 1);
                  }}
                />
              </>
            )}
          </View>
        )}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#FCF0E5' },
  diagnostic: { fontSize: 12, color: '#555', marginVertical: 4 },
  status: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
});
