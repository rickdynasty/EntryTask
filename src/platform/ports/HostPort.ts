/** EntryTask RN：平台无关 Port 契约：描述业务层可调用的宿主或基础设施能力。 */
export type ShellSnapshot = {
  tabId: number;
  depth: number;
  authenticated: boolean;
  sessionEpoch: number;
  revision: number;
};
export type HostCommand =
  | { type: 'selectTab'; tabId: number }
  | { type: 'navigation'; depth: number }
  | { type: 'authenticate' | 'logout' };
export interface HostPort {
  observeShell(listener: (snapshot: ShellSnapshot) => void): () => void;
  command(command: HostCommand): Promise<void>;
  readVisibility(): Promise<boolean>;
  writeVisibility(value: boolean): Promise<void>;
}
