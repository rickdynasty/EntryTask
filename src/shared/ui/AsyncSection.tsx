import React from 'react';
import { ActivityIndicator, Button, Text, View } from 'react-native';

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
