/** 页面会话内客户端状态契约，不依赖 Redux 或 React。 */
export type PageUiState = Readonly<{ offset: number; category: string }>;
export interface PageUiStatePort {
  getSnapshot(): PageUiState;
  subscribe(listener: () => void): () => void;
  update(value: Partial<PageUiState>): void;
}
