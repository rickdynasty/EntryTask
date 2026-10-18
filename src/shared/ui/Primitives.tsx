/** EntryTask RN：跨业务域共享 RN UI：集中呈现可复用控件，不承载业务请求与业务规则。 */
import React, { type PropsWithChildren } from 'react';
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { theme } from './theme';
import { Icon } from './Icon';
import { assetFor } from '@shared/assets';
export const ui = StyleSheet.create({
  heading: {
    fontSize: 19,
    fontWeight: '600',
    color: theme.colors.text,
    fontFamily: 'serif',
  },
  body: { fontSize: 14, color: theme.colors.text, lineHeight: 23 },
  muted: { fontSize: 12, color: theme.colors.muted, lineHeight: 20 },
  row: { flexDirection: 'row', alignItems: 'center' },
  between: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  gap: { gap: 12 },
});
/** 统一卡片底色和圆角，调用方仅传递内容与布局扩展样式。 */
export function Surface({
  children,
  style,
}: PropsWithChildren<{ style?: StyleProp<ViewStyle> }>) {
  return <View style={[styles.surface, style]}>{children}</View>;
}
/** 按禁用和提交状态锁定重复点击，并提供统一无障碍语义。 */
export function PrimaryButton({
  title,
  onPress,
  disabled = false,
  loading = false,
  small = false,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  small?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: disabled || loading }}
      disabled={disabled || loading}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        small && styles.small,
        (pressed || disabled || loading) && styles.dim,
      ]}
    >
      {loading ? (
        <ActivityIndicator color="#FFF" />
      ) : (
        <Text style={styles.buttonText}>{title}</Text>
      )}
    </Pressable>
  );
}
/** 组合标题、副标题和可选更多动作，统一对齐规则。 */
export function SectionHeading({
  title,
  subtitle,
  onMore,
}: {
  title: string;
  subtitle?: string;
  onMore?: () => void;
}) {
  return (
    <View style={[ui.between, styles.section]}>
      <View style={[ui.row, styles.sectionCopy]}>
        <Text style={ui.heading}>{title}</Text>
        {subtitle && <Text style={ui.muted}>{subtitle}</Text>}
      </View>
      {onMore && (
        <Pressable accessibilityLabel={`${title}更多`} onPress={onMore}>
          <Icon name="chevron" size={18} />
        </Pressable>
      )}
    </View>
  );
}
/** 根据属性绘制胶囊、文字下划线或均分分段控件。 */
export function ChoiceRail({
  items,
  selected,
  onChange,
  segmented = false,
  textOnly = false,
}: {
  items: readonly { id: string; title: string }[];
  selected: string;
  onChange: (id: string) => void;
  segmented?: boolean;
  textOnly?: boolean;
}) {
  return (
    <ScrollView
      horizontal
      keyboardShouldPersistTaps="handled"
      showsHorizontalScrollIndicator={false}
      style={segmented ? styles.segment : undefined}
      contentContainerStyle={[
        styles.choices,
        segmented && styles.segmentContent,
      ]}
    >
      {items.map(item => (
        <Pressable
          key={item.id}
          accessibilityRole="tab"
          accessibilityState={{ selected: item.id === selected }}
          onPress={() => onChange(item.id)}
          style={[
            styles.choice,
            segmented && styles.segmentChoice,
            textOnly && styles.textChoice,
            item.id === selected &&
              (segmented
                ? styles.segmentSelected
                : textOnly
                ? styles.textChoiceSelected
                : styles.choiceSelected),
          ]}
        >
          <Text
            style={[
              styles.choiceText,
              item.id === selected &&
                (segmented || textOnly
                  ? styles.segmentTextSelected
                  : styles.choiceTextSelected),
            ]}
          >
            {item.title}
          </Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}
/** 根据资源状态显示加载、错误重试或空态。 */
export function StatePanel({
  loading,
  error,
  empty,
  onRetry,
  children,
}: PropsWithChildren<{
  loading: boolean;
  error: string | null;
  empty?: boolean;
  onRetry: () => void;
}>) {
  return (
    <>
      {children}
      {loading && (
        <View style={styles.status}>
          <ActivityIndicator color={theme.colors.accent} />
          <Text style={ui.muted}>正在加载</Text>
        </View>
      )}
      {error && (
        <View style={styles.status} accessibilityRole="alert">
          <Text style={ui.muted}>{error}</Text>
          <PrimaryButton small title="重新加载" onPress={onRetry} />
        </View>
      )}
      {!loading && !error && empty && (
        <View style={styles.status}>
          <Text style={ui.muted}>暂无内容</Text>
        </View>
      )}
    </>
  );
}
/** 只按本地白名单资源名解析图片，未知图片不渲染。 */
export function Artwork({
  name,
  style,
}: {
  name: string;
  style?: StyleProp<ViewStyle>;
}) {
  const source = assetFor(name);
  return source ? (
    <View pointerEvents="none" style={style}>
      <Image source={source} resizeMode="contain" style={styles.art} />
    </View>
  ) : null;
}
/** 为金融演示内容提供统一用途说明。 */
export function DemoFootnote({
  text = '模拟数据，仅用于原型演示，不构成投资建议',
}: {
  text?: string;
}) {
  return <Text style={styles.footnote}>{text}</Text>;
}
const styles = StyleSheet.create({
  segmentContent: { flexGrow: 1 },
  textChoice: {
    backgroundColor: 'transparent',
    borderWidth: 0,
    paddingHorizontal: 4,
  },
  textChoiceSelected: { borderBottomWidth: 1, borderBottomColor: '#BC8D58' },
  sectionCopy: { gap: 12 },
  surface: { backgroundColor: '#FFF', borderRadius: 10, padding: 14, gap: 12 },
  button: {
    backgroundColor: theme.colors.danger,
    minHeight: 42,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
    borderRadius: 4,
  },
  small: { minHeight: 34, paddingHorizontal: 12 },
  buttonText: { color: '#FFF', fontSize: 15 },
  dim: { opacity: 0.55 },
  section: { minHeight: 30 },
  choices: { gap: 9, paddingVertical: 5 },
  choice: {
    borderRadius: 7,
    backgroundColor: '#FAFAFA',
    paddingHorizontal: 13,
    minHeight: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#FFF',
  },
  choiceSelected: { backgroundColor: '#CA9966' },
  choiceText: { fontSize: 14, color: '#8B8884' },
  choiceTextSelected: { color: '#FFF', fontWeight: '600' },
  segment: { borderRadius: 5, backgroundColor: '#F3F3F3' },
  segmentChoice: {
    flex: 1,
    minWidth: 125,
    backgroundColor: 'transparent',
    borderWidth: 0,
  },
  segmentSelected: { backgroundColor: '#FFF' },
  segmentTextSelected: { color: '#222', fontWeight: '600' },
  status: { padding: 16, gap: 10, alignItems: 'center' },
  art: { width: '100%', height: '100%' },
  footnote: {
    textAlign: 'center',
    color: '#A09A92',
    fontSize: 10,
    lineHeight: 16,
    marginVertical: 12,
  },
});
