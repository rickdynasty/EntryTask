/** 四根页公共组件：只绘制输入，网络/会话和写操作由VM及宿主协调。 */
import React, {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  AccessibilityInfo,
  Animated,
  AppState,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewToken,
} from 'react-native';
import type { PageUiStatePort } from '@shared/domain/PageUiState';
import { formatMinor } from '@shared/formatting/numbers';
import { Artwork, Surface, ui } from './Primitives';
import { Icon } from './Icon';

const RegionActive = createContext(false);
export type PageRegion = { id: string; content: ReactNode };
/** 单个主列表；位置存在会话UI Store，延迟到内容布局就绪后恢复一次。 */
export function PageFrame({
  regions,
  state,
  active,
  ready = true,
}: {
  ready?: boolean;
  regions: PageRegion[];
  state: PageUiStatePort;
  active: boolean;
}) {
  const list = useRef<FlatList<PageRegion>>(null);
  const target = useRef(state.getSnapshot().offset);
  const offset = useRef(target.current);
  const bounds = useRef({ height: 0, viewport: 0 });
  const restored = useRef(false);
  const [visible, setVisible] = useState<ReadonlySet<string>>(new Set());
  const viewability = useRef(
    ({ viewableItems }: { viewableItems: ViewToken<PageRegion>[] }) =>
      setVisible(new Set(viewableItems.map(item => item.item.id))),
  );
  const restore = () => {
    const { height, viewport } = bounds.current;
    if (!restored.current && ready && height > 0 && viewport > 0) {
      restored.current = true;
      offset.current = Math.min(target.current, Math.max(0, height - viewport));
      list.current?.scrollToOffset({ offset: offset.current, animated: false });
    }
  };
  useEffect(restore);
  useEffect(() => () => state.update({ offset: offset.current }), [state]);
  return (
    <FlatList
      ref={list}
      style={styles.page}
      data={regions}
      keyExtractor={item => item.id}
      renderItem={({ item }) => (
        <RegionActive.Provider value={active && visible.has(item.id)}>
          <View style={styles.region}>{item.content}</View>
        </RegionActive.Provider>
      )}
      onViewableItemsChanged={viewability.current}
      viewabilityConfig={{ itemVisiblePercentThreshold: 20 }}
      ListFooterComponent={<AppFooterLogo />}
      showsVerticalScrollIndicator={false}
      onLayout={event => {
        bounds.current.viewport = event.nativeEvent.layout.height;
        restore();
      }}
      scrollEventThrottle={100}
      onScroll={event => {
        offset.current = event.nativeEvent.contentOffset.y;
      }}
      onScrollBeginDrag={() => {
        restored.current = true;
      }}
      onContentSizeChange={(_, height) => {
        bounds.current.height = height;
        restore();
      }}
      onScrollEndDrag={() => state.update({ offset: offset.current })}
      onMomentumScrollEnd={() => state.update({ offset: offset.current })}
    />
  );
}
/** 动画仅在前台、当前Tab和可见区域内运行；effect清理负责停止定时器。 */
function useCarouselClock(
  count: number,
  paused: boolean,
  advance: () => void,
  stop?: () => void,
) {
  const visible = useContext(RegionActive);
  const [foreground, setForeground] = useState(
    AppState.currentState === 'active',
  );
  const [reduced, setReduced] = useState(false);
  const callback = useRef(advance);
  callback.current = advance;
  const stopCallback = useRef(stop);
  stopCallback.current = stop;
  useEffect(() => {
    const app = AppState.addEventListener('change', value =>
      setForeground(value === 'active'),
    );
    let alive = true;
    void AccessibilityInfo.isReduceMotionEnabled().then(value => {
      if (alive) setReduced(value);
    });
    const motion = AccessibilityInfo.addEventListener(
      'reduceMotionChanged',
      setReduced,
    );
    return () => {
      alive = false;
      app.remove();
      motion.remove();
    };
  }, []);
  useEffect(() => {
    if (!visible || !foreground || paused || count < 2) {
      stopCallback.current?.();
      return;
    }
    const timer = setInterval(() => callback.current(), 5000);
    return () => {
      clearInterval(timer);
      stopCallback.current?.();
    };
  }, [visible, foreground, paused, count]);
  return reduced;
}
export function BannerCarousel<
  T extends { id: string; title: string; subtitle: string; assetKey: string },
>({ items, onSelect }: { items: T[]; onSelect: (item: T) => void }) {
  const list = useRef<FlatList<T>>(null);
  const [width, setWidth] = useState(1);
  const [index, setIndex] = useState(0);
  const selectedId = useRef<string | undefined>(undefined);
  const [touching, setTouching] = useState(false);
  const id = items[index]?.id;
  const reduced = useCarouselClock(items.length, touching, () => {
    const next = (index + 1) % items.length;
    selectedId.current = items[next]?.id;
    setIndex(next);
    list.current?.scrollToOffset({ offset: next * width, animated: !reduced });
  });
  useEffect(() => {
    const next = Math.max(
      0,
      items.findIndex(item => item.id === selectedId.current),
    );
    selectedId.current = items[next]?.id;
    setIndex(next);
    list.current?.scrollToOffset({ offset: next * width, animated: false });
  }, [items, width]);
  if (!items.length) return null;
  return (
    <View onLayout={event => setWidth(event.nativeEvent.layout.width)}>
      <FlatList
        ref={list}
        horizontal
        pagingEnabled
        data={items}
        keyExtractor={item => item.id}
        showsHorizontalScrollIndicator={false}
        onTouchStart={() => setTouching(true)}
        onTouchEnd={() => setTouching(false)}
        onTouchCancel={() => setTouching(false)}
        onMomentumScrollEnd={event => {
          setTouching(false);
          const next = Math.max(
            0,
            Math.min(
              items.length - 1,
              Math.round(event.nativeEvent.contentOffset.x / width),
            ),
          );
          selectedId.current = items[next]?.id;
          setIndex(next);
        }}
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={item.title}
            onPress={() => onSelect(item)}
            style={[styles.banner, { width }]}
          >
            <Artwork name={item.assetKey} style={styles.bannerArt} />
            <View style={styles.bannerCopy}>
              <Text style={styles.bannerTitle}>{item.title}</Text>
              <Text style={styles.bannerSubtitle}>{item.subtitle}</Text>
            </View>
          </Pressable>
        )}
      />
      <View style={styles.dots}>
        {items.map(item => (
          <View
            key={item.id}
            style={[styles.dot, item.id === id && styles.selectedDot]}
          />
        ))}
      </View>
    </View>
  );
}
/** 正文独立向上切换；固定裁剪高度，长文点击后由调用方展示。 */
export function MarketTickerCarousel({
  items,
  onSelect,
}: {
  items: { id: string; title: string }[];
  onSelect: (title: string) => void;
}) {
  const [index, setIndex] = useState(0);
  const selectedId = useRef<string | undefined>(undefined);
  const [touching, setTouching] = useState(false);
  const position = useRef(new Animated.Value(0)).current;
  const reduced = useCarouselClock(
    items.length,
    touching,
    () => {
      if (reduced) {
        setIndex(value => {
          const next = (value + 1) % items.length;
          selectedId.current = items[next]?.id;
          return next;
        });
        return;
      }
      Animated.timing(position, {
        toValue: -30,
        duration: 220,
        useNativeDriver: true,
      }).start(({ finished }) => {
        if (finished) {
          setIndex(value => {
            const next = (value + 1) % items.length;
            selectedId.current = items[next]?.id;
            return next;
          });
          position.setValue(0);
        }
      });
    },
    () => {
      position.stopAnimation();
      position.setValue(0);
    },
  );
  const active = useContext(RegionActive);
  useEffect(() => {
    if (!active) {
      position.stopAnimation();
      position.setValue(0);
    }
    return () => position.stopAnimation();
  }, [active, position]);
  useEffect(() => {
    position.stopAnimation();
    const next = Math.max(
      0,
      items.findIndex(item => item.id === selectedId.current),
    );
    selectedId.current = items[next]?.id;
    setIndex(next);
    position.setValue(0);
  }, [items, position]);
  const current = items[index];
  if (!current) return null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`市场解读：${current.title}`}
      onTouchStart={() => setTouching(true)}
      onTouchEnd={() => setTouching(false)}
      onTouchCancel={() => setTouching(false)}
      onPress={() => onSelect(current.title)}
      style={styles.ticker}
    >
      <Text style={styles.marketLabel}>市场解读</Text>
      <View style={styles.clip}>
        <Animated.View style={{ transform: [{ translateY: position }] }}>
          <Text numberOfLines={1} style={styles.tickerText}>
            {current.title}
          </Text>
          <Text numberOfLines={1} style={styles.tickerText}>
            {items[(index + 1) % items.length]?.title}
          </Text>
        </Animated.View>
      </View>
      <Icon name="chevron" size={16} />
    </Pressable>
  );
}
/** 品牌收尾只作为列表末项。标志沿用工程线稿，正式品牌素材仍需视觉验收。 */
export function AppFooterLogo() {
  return (
    <View
      accessible
      accessibilityLabel="中信银行，CHINA CITIC BANK，让财富有温度"
      style={styles.footer}
    >
      <View style={ui.row}>
        <Icon name="bank-brand" size={36} color="#C92336" />
        <View>
          <Text style={styles.brand}>中信银行</Text>
          <Text style={styles.english}>CHINA CITIC BANK</Text>
        </View>
      </View>
      <Text style={styles.tagline}>让　财　富　有　温　度</Text>
    </View>
  );
}
export function MoneyText({
  value,
  visible,
  large = false,
  color = '#292623',
}: {
  value?: { amountMinor: string; minorUnit: number } | null | undefined;
  visible: boolean;
  large?: boolean;
  color?: string;
}) {
  return (
    <Text
      accessibilityLabel={visible ? undefined : '金额已隐藏'}
      style={[large ? styles.money : styles.smallMoney, { color }]}
    >
      {!visible
        ? '******'
        : value
        ? formatMinor(value.amountMinor, value.minorUnit)
        : '—'}
    </Text>
  );
}
export function EyeToggle({
  visible,
  onToggle,
}: {
  visible: boolean;
  onToggle: () => void;
}) {
  const scale = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    Animated.sequence([
      Animated.timing(scale, {
        toValue: 0.8,
        duration: 80,
        useNativeDriver: true,
      }),
      Animated.timing(scale, {
        toValue: 1,
        duration: 80,
        useNativeDriver: true,
      }),
    ]).start();
    return () => scale.stopAnimation();
  }, [visible, scale]);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={visible ? '隐藏资产' : '显示资产'}
      onPress={onToggle}
      style={styles.eye}
    >
      <Animated.View style={{ transform: [{ scale }] }}>
        <Icon name={visible ? 'eye-open' : 'eye'} size={21} color="#9B8D7B" />
      </Animated.View>
    </Pressable>
  );
}
export function AssetPrivacyCard({
  total,
  income,
  visible,
  onToggle,
}: {
  total?: { amountMinor: string; minorUnit: number } | null | undefined;
  income?: { amountMinor: string; minorUnit: number } | null | undefined;
  visible: boolean;
  onToggle: () => void;
}) {
  return (
    <Surface>
      <View style={ui.between}>
        <Text style={ui.heading}>资产总览</Text>
        <EyeToggle visible={visible} onToggle={onToggle} />
      </View>
      <View style={ui.between}>
        <View>
          <MoneyText value={total} visible={visible} large />
          <Text style={ui.muted}>总资产（元）</Text>
        </View>
        <View>
          <MoneyText value={income} visible={visible} />
          <Text style={ui.muted}>最新收益（元）</Text>
        </View>
      </View>
    </Surface>
  );
}
export function DemoActionSheet({
  title,
  onClose,
}: {
  title: string | null;
  onClose: () => void;
}) {
  return (
    <Modal
      visible={title !== null}
      transparent
      animationType="fade"
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <Surface>
          <Text style={ui.heading}>{title}</Text>
          <Text style={ui.body}>本入口仅作界面演示，不发起真实银行业务。</Text>
          <Pressable
            accessibilityRole="button"
            onPress={onClose}
            style={styles.eye}
          >
            <Text style={ui.body}>我知道了</Text>
          </Pressable>
        </Surface>
      </View>
    </Modal>
  );
}
const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: '#F5F5F5' },
  region: { marginHorizontal: 12, marginTop: 12 },
  banner: {
    minHeight: 158,
    backgroundColor: '#FBE9D8',
    borderRadius: 10,
    overflow: 'hidden',
    justifyContent: 'center',
  },
  bannerArt: { position: 'absolute', right: 0, width: '58%', height: 145 },
  bannerCopy: { padding: 18, width: '70%' },
  bannerTitle: {
    fontSize: 27,
    color: '#915319',
    fontFamily: 'serif',
    fontWeight: '700',
  },
  bannerSubtitle: { fontSize: 13, color: '#985C2B', marginTop: 9 },
  dots: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 6,
    marginTop: 8,
  },
  dot: { width: 5, height: 5, borderRadius: 3, backgroundColor: '#D6CBC0' },
  selectedDot: { width: 15, backgroundColor: '#AF8051' },
  ticker: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 48,
    gap: 9,
    backgroundColor: '#FFF',
    borderRadius: 8,
    paddingHorizontal: 10,
  },
  marketLabel: { fontSize: 13, color: '#A27740', fontWeight: '700' },
  clip: { height: 30, flex: 1, overflow: 'hidden' },
  tickerText: { height: 30, lineHeight: 30, color: '#47413A', fontSize: 12 },
  footer: { paddingVertical: 38, alignItems: 'center', gap: 4 },
  brand: {
    fontFamily: 'serif',
    fontWeight: '700',
    fontSize: 24,
    color: '#242321',
  },
  english: { fontSize: 8, color: '#242321', letterSpacing: 0.8 },
  tagline: { fontSize: 9, color: '#6F6253' },
  money: { color: '#292623', fontSize: 25, fontWeight: '600' },
  smallMoney: { color: '#292623', fontSize: 19, fontWeight: '600' },
  eye: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  overlay: {
    flex: 1,
    justifyContent: 'center',
    padding: 28,
    backgroundColor: '#0006',
  },
});
