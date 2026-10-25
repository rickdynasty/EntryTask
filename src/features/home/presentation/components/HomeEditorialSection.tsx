/** 首页运营区只接受有限布局和展示属性，不执行远端脚本，不直接访问网络。 */
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { PageActions } from '@features/services/public';
import { ServiceGrid } from '@shared/ui/ServiceGrid';
import { CampaignCard } from '@shared/ui/CampaignCard';
import { Icon } from '@shared/ui/Icon';
import { SectionHeading, Surface, ui } from '@shared/ui/Primitives';
import type { HomeSection } from '../../domain/Home';
export function HomeEditorialSection({
  section,
  actions,
}: {
  section: HomeSection;
  actions: PageActions;
}) {
  return (
    <Surface style={styles.section}>
      {!!section.title && (
        <SectionHeading title={section.title} subtitle={section.subtitle} />
      )}
      {section.style === 'menu' ? (
        <ServiceGrid
          items={section.entries}
          columns={section.entries.length === 4 ? 4 : 3}
          onSelect={item => actions.open(item.target)}
        />
      ) : section.style === 'banner' ? (
        section.entries.map(item => (
          <CampaignCard
            key={item.id}
            title={item.title}
            subtitle={item.subtitle}
            assetKey={item.assetKey}
            tone={item.tone}
            compact
            onPress={() => actions.open(item.target)}
          />
        ))
      ) : section.style === 'cards' ? (
        <View style={styles.tiles}>
          {section.entries.map(item => (
            <Pressable
              key={item.id}
              accessibilityRole="button"
              onPress={() => actions.open(item.target)}
              style={[styles.tile, item.tone === 'blue' && styles.blue]}
            >
              <Icon name={item.icon} color="#B78C5D" size={28} />
              <Text style={styles.title}>{item.title}</Text>
              <Text style={ui.muted}>{item.subtitle}</Text>
            </Pressable>
          ))}
        </View>
      ) : (
        section.entries.map(item => (
          <Pressable
            key={item.id}
            accessibilityRole="button"
            onPress={() => actions.open(item.target)}
            style={styles.news}
          >
            <View style={styles.copy}>
              <Text style={ui.body}>{item.title}</Text>
              <Text style={ui.muted}>{item.subtitle}</Text>
            </View>
            <Icon name={item.icon} size={40} color="#B78C5D" />
          </Pressable>
        ))
      )}
    </Surface>
  );
}
const styles = StyleSheet.create({
  section: { backgroundColor: '#FFF9F3' },
  tiles: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  tile: {
    width: '48%',
    backgroundColor: '#FFF3EE',
    borderRadius: 6,
    padding: 14,
    gap: 10,
  },
  blue: { backgroundColor: '#F0F3FD' },
  title: { fontSize: 16, color: '#B78C5D' },
  news: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
    borderBottomColor: '#F0ECE7',
    borderBottomWidth: 0.5,
  },
  copy: { flex: 1, gap: 8 },
});
