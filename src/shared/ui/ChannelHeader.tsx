/** EntryTask RN：跨业务域共享 RN UI：集中呈现可复用控件，不承载业务请求与业务规则。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Icon } from './Icon';
import { Artwork } from './Primitives';
import { theme } from './theme';
export type HeaderActions = {
  search: () => void;
  messages: () => void;
  advisor: () => void;
  scan: () => void;
  settings: () => void;
  login: () => void;
};
/** 按页面模式组合搜索、分段、设置、消息与客服入口。 */
export function ChannelHeader({
  mode,
  actions,
  tabs = [],
  selected = '',
  onSelect,
  placeholder = '搜索产品、服务',
  unread = 0,
  authenticated = false,
}: {
  mode: 'home' | 'channel' | 'profile';
  actions: HeaderActions;
  tabs?: readonly string[];
  selected?: string;
  onSelect?: (value: string) => void;
  placeholder?: string;
  unread?: number;
  authenticated?: boolean;
}) {
  return (
    <View style={styles.header}>
      {mode === 'home' ? (
        <>
          <Pressable
            accessibilityLabel="扫一扫"
            onPress={actions.scan}
            hitSlop={8}
          >
            <Icon name="scan" size={24} />
          </Pressable>
          <Pressable
            accessibilityLabel="搜索"
            onPress={actions.search}
            style={styles.search}
          >
            <Icon name="search" size={20} />
            <Text style={styles.placeholder}>{placeholder}</Text>
          </Pressable>
        </>
      ) : mode === 'profile' ? (
        <Pressable onPress={actions.login} style={styles.flex}>
          <Text style={styles.profileTitle}>
            {authenticated ? '我的' : '登录'}
          </Text>
        </Pressable>
      ) : (
        <View style={[styles.tabs, styles.flex]}>
          {tabs.map(tab => (
            <Pressable
              key={tab}
              accessibilityRole="tab"
              accessibilityState={{ selected: tab === selected }}
              onPress={() => onSelect?.(tab)}
              style={styles.tab}
            >
              <Text
                style={[styles.tabText, tab === selected && styles.selected]}
              >
                {tab}
              </Text>
              {tab === selected && <View style={styles.arc} />}
            </Pressable>
          ))}
        </View>
      )}
      {mode !== 'home' && (
        <Pressable
          accessibilityLabel={mode === 'profile' ? '设置' : '搜索'}
          onPress={mode === 'profile' ? actions.settings : actions.search}
          hitSlop={8}
        >
          <Icon name={mode === 'profile' ? 'settings' : 'search'} size={24} />
        </Pressable>
      )}
      <Pressable
        accessibilityLabel={`消息，${unread}条未读`}
        onPress={actions.messages}
        style={styles.message}
      >
        <Icon name="message" size={25} />
        {unread > 0 && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{unread > 99 ? '99+' : unread}</Text>
          </View>
        )}
      </Pressable>
      <Pressable accessibilityLabel="在线客服" onPress={actions.advisor}>
        <Artwork name="advisor-avatar" style={styles.avatar} />
      </Pressable>
    </View>
  );
}
const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
    paddingHorizontal: 13,
    height: 64,
  },
  flex: { flex: 1 },
  tabs: { flexDirection: 'row', alignItems: 'center', gap: 20 },
  tab: { minHeight: 43, justifyContent: 'center' },
  tabText: { fontFamily: 'serif', fontSize: 19, color: '#37332F' },
  selected: { fontWeight: '700', color: '#181715', fontSize: 22 },
  arc: {
    position: 'absolute',
    bottom: 0,
    left: 6,
    width: 22,
    height: 4,
    backgroundColor: '#CCA16F',
    borderBottomLeftRadius: 20,
    borderBottomRightRadius: 20,
  },
  search: {
    flex: 1,
    height: 35,
    backgroundColor: '#FFF',
    borderColor: '#DDD8D1',
    borderWidth: 1,
    borderRadius: 7,
    paddingHorizontal: 10,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  placeholder: { color: '#37332F', fontSize: 14 },
  message: { position: 'relative' },
  badge: {
    position: 'absolute',
    top: -10,
    right: -8,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: theme.colors.danger,
    backgroundColor: '#FFF',
    minWidth: 17,
    alignItems: 'center',
  },
  badgeText: { fontSize: 10, color: theme.colors.danger },
  avatar: { width: 27, height: 30 },
  profileTitle: { fontSize: 15, fontWeight: '600', color: '#242321' },
});
