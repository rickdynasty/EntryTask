import React, { useEffect, useState, useSyncExternalStore } from 'react';
import {
  ActivityIndicator,
  Button,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { HomeScreen } from '@features/home/public';
import { normalizeError } from '@shared/domain/AppError';
import { createApplication } from './CompositionRoot';

// The single registered component creates one application graph per mounted Surface.
// Android owns a single ReactHost; Native-only HomeFragment doesn't mount this graph.
export default function App() {
  const [application] = useState(createApplication);
  const [phase, setPhase] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const state = useSyncExternalStore(
    application.home.subscribe,
    application.home.getSnapshot,
  );
  useEffect(() => {
    let active = true;
    void application
      .bootstrap()
      .then(() => {
        if (active) {
          application.home.activate();
          setPhase('ready');
        }
      })
      .catch(reason => {
        const safe = normalizeError(reason);
        application.logger.log('error', 'bootstrap', 'bootstrap.failed', {
          code: safe.code,
        });
        if (active) {
          setError(safe.message);
          setPhase('error');
        }
      });
    return () => {
      active = false;
      application.home.deactivate();
    };
  }, [application, attempt]);
  useEffect(() => () => application.dispose(), [application]);
  return (
    <SafeAreaProvider>
      <SafeAreaView edges={['top', 'left', 'right']} style={styles.root}>
        {phase === 'ready' ? (
          <HomeScreen
            state={state}
            refresh={application.home.refresh}
            selectService={application.home.selectService}
          />
        ) : (
          <View style={styles.status}>
            {phase === 'loading' ? (
              <ActivityIndicator accessibilityLabel="正在准备应用" />
            ) : (
              <>
                <Text>{error}</Text>
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
  root: { flex: 1, backgroundColor: '#F6F6F7' },
  status: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
});
