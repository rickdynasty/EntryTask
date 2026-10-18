/** EntryTask RN：跨业务域共享 RN UI：集中呈现可复用控件，不承载业务请求与业务规则。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Artwork } from './Primitives';
/** 促销图与文案分离，尺寸及色调由组件属性控制。 */
export function CampaignCard({
  title,
  subtitle,
  assetKey,
  tone = 'gold',
  onPress,
  compact = false,
}: {
  title: string;
  subtitle: string;
  assetKey: string;
  tone?: string;
  onPress: () => void;
  compact?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={[
        styles.card,
        tone === 'blue'
          ? styles.blue
          : tone === 'rose'
          ? styles.rose
          : styles.gold,
        compact && styles.compact,
      ]}
    >
      <View style={styles.copy}>
        <Text style={[styles.title, compact && styles.compactTitle]}>
          {title}
        </Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>
      <Artwork name={assetKey} style={styles.art} />
    </Pressable>
  );
}
const styles = StyleSheet.create({
  blue: { backgroundColor: '#F1F5FD' },
  rose: { backgroundColor: '#FFF6F4' },
  gold: { backgroundColor: '#F3DDBE' },
  card: {
    minHeight: 94,
    borderRadius: 8,
    overflow: 'hidden',
    justifyContent: 'center',
  },
  compact: { minHeight: 90 },
  copy: { padding: 20, zIndex: 1, maxWidth: '77%' },
  title: {
    color: '#8E4C15',
    fontSize: 23,
    fontWeight: '700',
    fontFamily: 'serif',
  },
  compactTitle: { fontSize: 19 },
  subtitle: { fontSize: 12, color: '#AA855D', marginTop: 7 },
  art: {
    position: 'absolute',
    right: 4,
    bottom: 0,
    width: '36%',
    height: '100%',
  },
});
