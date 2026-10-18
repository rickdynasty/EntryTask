/** EntryTask RN：跨业务域共享 RN UI：集中呈现可复用控件，不承载业务请求与业务规则。 */
import React from 'react';
import { ActivityIndicator, Button, Text, View } from 'react-native';

/** 分别呈现加载、错误重试和仍可用的子内容。 */
export function AsyncSection({
  loading,
  error,
  onRetry,
  children,
}: React.PropsWithChildren<{
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}>) {
  return (
    <View>
      {loading && <ActivityIndicator accessibilityLabel="正在加载" />}
      {error && (
        <View accessibilityRole="alert">
          <Text>{error}</Text>
          <Button title="重试" onPress={onRetry} />
        </View>
      )}
      {children}
    </View>
  );
}
