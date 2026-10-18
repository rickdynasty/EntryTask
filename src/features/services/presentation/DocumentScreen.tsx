/** EntryTask RN：通用服务与详情业务域模块：负责页面呈现与 ViewModel 状态流转，不跨域持有页面私有实现。 */
import React, { useState, useSyncExternalStore } from 'react';
import {
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import type { DocumentViewModel } from './DocumentViewModel';
import type { Target } from '../domain/Content';
import { useResource } from '@shared/presentation/useResource';
import {
  Artwork,
  ChoiceRail,
  DemoFootnote,
  PrimaryButton,
  StatePanel,
  Surface,
  ui,
} from '@shared/ui/Primitives';
import { Icon } from '@shared/ui/Icon';
/** 把搜索、翻页及写入动作交给详情 ViewModel 统一处理。 */
export function DocumentScreen({
  model,
  target,
  onOpen,
  authenticated,
  assetsVisible,
  onAuthenticate,
  onToggleVisibility,
}: {
  model: DocumentViewModel;
  target: Target;
  onOpen: (target: Target) => void;
  authenticated: boolean;
  assetsVisible: boolean;
  onAuthenticate: () => void;
  onToggleVisibility: () => void;
}) {
  // 通用详情视图只把用户动作交给 ViewModel，数据来源和写入策略由工厂决定。
  const resource = useResource(model.resource);
  const action = useSyncExternalStore(model.subscribe, model.getSnapshot);
  const [keyword, setKeyword] = useState('');
  const [scope, setScope] = useState('services');
  const data = resource.data;
  return (
    <FlatList
      style={styles.page}
      contentContainerStyle={styles.content}
      data={data?.rows ?? []}
      keyExtractor={item => item.id}
      keyboardShouldPersistTaps="handled"
      onRefresh={model.resource.refresh}
      refreshing={resource.loading && !!data}
      renderItem={({ item }) => (
        <Pressable
          onPress={() => {
            if (item.target) onOpen(item.target);
          }}
          disabled={!item.target}
        >
          <Surface style={styles.row}>
            <View style={styles.rowCopy}>
              <Text
                style={[ui.heading, item.read === false && styles.unreadTitle]}
              >
                {item.title}
              </Text>
              <Text style={ui.muted}>{item.subtitle}</Text>
              {!!item.value && <Text style={styles.value}>{item.value}</Text>}
            </View>
            {item.assetKey && (
              <Artwork name={item.assetKey} style={styles.art} />
            )}
            {item.target && <Icon name="chevron" size={18} />}
          </Surface>
        </Pressable>
      )}
      ListHeaderComponent={
        <View style={ui.gap}>
          {target.kind === 'search' && (
            <Surface>
              <View style={[ui.row, styles.searchRow]}>
                <TextInput
                  accessibilityLabel="搜索关键词"
                  placeholder="搜索服务、产品、资讯、活动"
                  value={keyword}
                  onChangeText={setKeyword}
                  returnKeyType="search"
                  onSubmitEditing={() => model.search(keyword, scope)}
                  style={styles.input}
                />
                <PrimaryButton
                  small
                  title="搜索"
                  onPress={() => model.search(keyword, scope)}
                />
              </View>
              <ChoiceRail
                items={[
                  { id: 'services', title: '服务' },
                  { id: 'products', title: '产品' },
                  { id: 'articles', title: '资讯' },
                  { id: 'activities', title: '活动' },
                ]}
                selected={scope}
                onChange={value => {
                  setScope(value);
                  model.search(keyword, value);
                }}
              />
            </Surface>
          )}
          <StatePanel
            loading={resource.loading && !data}
            error={resource.error}
            onRetry={model.resource.refresh}
          />
          {data && (
            <>
              <Surface>
                <Text style={styles.title}>{data.title}</Text>
                {target.kind === 'assets' && (
                  <PrimaryButton
                    small
                    title={assetsVisible ? '隐藏金额' : '显示金额'}
                    onPress={onToggleVisibility}
                  />
                )}
                {!!data.subtitle && (
                  <Text style={ui.muted}>{data.subtitle}</Text>
                )}
                {data.metrics.map(metric => (
                  <View key={metric.label} style={ui.between}>
                    <Text style={ui.muted}>{metric.label}</Text>
                    <Text style={ui.body}>
                      {target.kind === 'assets' &&
                      !assetsVisible &&
                      metric.value.startsWith('¥')
                        ? '******'
                        : metric.value}
                    </Text>
                  </View>
                ))}
                {data.paragraphs.map((p, i) => (
                  <Text key={i} style={ui.body}>
                    {p}
                  </Text>
                ))}
                {data.actionLabel && (
                  <PrimaryButton
                    title={!authenticated ? '登录后继续' : data.actionLabel}
                    disabled={data.actionDisabled ?? false}
                    loading={action.busy}
                    onPress={authenticated ? model.submit : onAuthenticate}
                  />
                )}
                {target.kind === 'product' && (
                  <PrimaryButton
                    title="了解更多演示说明"
                    onPress={() =>
                      onOpen({
                        kind: 'demo',
                        title: '产品服务说明',
                        requiresAuth: false,
                      })
                    }
                  />
                )}
                {action.notice && (
                  <Text style={styles.notice}>{action.notice}</Text>
                )}
                {action.error && (
                  <Text accessibilityRole="alert" style={styles.error}>
                    {action.error}
                  </Text>
                )}
              </Surface>
              {data.rows.length === 0 &&
                data.metrics.length === 0 &&
                data.paragraphs.length === 0 &&
                !resource.loading && (
                  <Text style={styles.empty}>
                    {target.kind === 'search'
                      ? '输入关键词，查找您需要的内容'
                      : '暂无记录'}
                  </Text>
                )}
            </>
          )}
        </View>
      }
      ListFooterComponent={
        <View style={ui.gap}>
          {data && (data.hasMore || data.page > 1) && (
            <View style={[ui.between, styles.paginationRow]}>
              <PrimaryButton
                small
                title="上一页"
                disabled={data.page <= 1}
                onPress={() => model.page(data.page - 1)}
              />
              <Text style={ui.muted}>第{data.page}页</Text>
              <PrimaryButton
                small
                title="下一页"
                disabled={!data.hasMore}
                onPress={() => model.page(data.page + 1)}
              />
            </View>
          )}
          <DemoFootnote />
        </View>
      }
    />
  );
}
const styles = StyleSheet.create({
  rowCopy: { flex: 1, gap: 6 },
  unreadTitle: { color: '#B88044' },
  searchRow: { gap: 10 },
  paginationRow: { marginTop: 16 },
  page: { flex: 1, backgroundColor: '#F5F5F5' },
  content: { padding: 14, gap: 12 },
  row: { flexDirection: 'row', alignItems: 'center', marginTop: 12 },
  title: { fontSize: 23, fontWeight: '600', color: '#242321', lineHeight: 32 },
  value: { fontSize: 22, color: '#F43E50' },
  art: { width: 75, height: 70 },
  input: { flex: 1, minHeight: 42, fontSize: 14, color: '#242321' },
  notice: { color: '#58927C', fontSize: 13 },
  error: { color: '#C23B4A', fontSize: 13 },
  empty: { padding: 25, textAlign: 'center', color: '#8B8884' },
});
