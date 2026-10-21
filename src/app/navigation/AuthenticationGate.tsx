/** 页面门禁：在业务组件挂载前阻断访问；认证结果以 Native 会话事件为准。 */
import React, { useEffect } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { PrimaryButton, ui } from '@shared/ui/Primitives';
import type { Application } from '../CompositionRoot';

export function AuthenticationGate({
  application,
  pageId,
  autoRequest = false,
  onHelp,
  onBack,
}: {
  application: Application;
  pageId: string;
  autoRequest?: boolean;
  onHelp?: () => void;
  onBack?: () => void;
}) {
  const requestLogin = () => {
    application.logger.log('info', 'session', 'authentication.requested', {
      pageId,
    });
    application.shell.command({ type: 'authenticate' });
  };
  useEffect(() => {
    application.logger.log('warn', 'session', 'authentication.required', {
      pageId,
    });
    // 受保护子页每次进入只自动请求一次；取消不改变页面身份，也不会循环弹窗。
    // 根页首次认证由 Native 负责，取消后只保留手动重试。
    if (autoRequest) application.shell.command({ type: 'authenticate' });
  }, [application, autoRequest, pageId]);
  return (
    <SafeAreaView style={styles.gate}>
      <View style={styles.content}>
        <Text style={ui.heading}>登录后继续</Text>
        <Text style={ui.body}>请完成登录后访问此页面。</Text>
        <PrimaryButton title="前往登录" onPress={requestLogin} />
        {onHelp && <PrimaryButton title="帮助中心" onPress={onHelp} />}
        {onBack && <PrimaryButton title="返回" onPress={onBack} />}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  gate: { flex: 1, justifyContent: 'center', backgroundColor: '#FCF0E5' },
  content: { alignItems: 'center', paddingHorizontal: 28, gap: 16 },
});
