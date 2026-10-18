/** EntryTask RN：跨业务域共享 RN UI：集中呈现可复用控件，不承载业务请求与业务规则。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Icon } from './Icon';
import { Artwork } from './Primitives';
/** 复用同一列宽算法，按属性切换快捷素材或线性图标。 */
export function ServiceGrid<
  T extends { id: string; title: string; icon: string; badge?: string },
>({
  items,
  onSelect,
  columns = 5,
  gold = false,
}: {
  items: readonly T[];
  onSelect: (item: T) => void;
  columns?: 4 | 5;
  gold?: boolean;
}) {
  return (
    <View style={styles.grid}>
      {items.map((item, index) => (
        <Pressable
          key={item.id}
          accessibilityRole="button"
          accessibilityLabel={item.title}
          onPress={() => onSelect(item)}
          style={[
            styles.item,
            { width: `${100 / columns}%` },
            gold && styles.gold,
          ]}
        >
          <View>
            {gold ? (
              <Artwork name={`quick-${index}`} style={styles.quick} />
            ) : (
              <Icon name={item.icon} size={31} />
            )}
            {item.badge && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{item.badge}</Text>
              </View>
            )}
          </View>
          <Text
            numberOfLines={2}
            style={[styles.title, gold && styles.goldTitle]}
          >
            {item.title}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}
const styles = StyleSheet.create({
  grid: { flexDirection: 'row', flexWrap: 'wrap', paddingVertical: 5 },
  item: { alignItems: 'center', gap: 9, paddingVertical: 10, minHeight: 75 },
  title: { fontSize: 13, color: '#282725', textAlign: 'center' },
  gold: { paddingVertical: 10, gap: 9 },
  goldTitle: { fontSize: 15 },
  quick: { width: 49, height: 49 },
  badge: {
    position: 'absolute',
    top: -9,
    left: 21,
    backgroundColor: '#F45161',
    borderRadius: 7,
    paddingHorizontal: 4,
    minWidth: 27,
  },
  badgeText: { color: '#FFF', fontSize: 9 },
});
