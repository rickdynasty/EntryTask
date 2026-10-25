/** EntryTask RN：Native Shell 状态适配：接收宿主快照、隔离会话并转发宿主命令。 */
import type { HostPort, ShellSnapshot } from '@platform/ports/HostPort';
import type { LoggerPort } from '@platform/ports/LoggerPort';
export type ShellState = ShellSnapshot & { assetsVisible: boolean };
export class ShellViewModel {
  private state: ShellState = {
    tabId: 1,
    depth: 0,
    authenticated: false,
    sessionEpoch: 0,
    revision: 0,
    assetsVisible: false,
  };
  private readonly listeners = new Set<() => void>();
  private stop: (() => void) | undefined;
  constructor(
    private readonly host: HostPort,
    private readonly logger: LoggerPort,
    private readonly clearSessionQueries: () => void,
  ) {}
  getSnapshot = (): ShellState => this.state;
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };
  private emit() {
    this.listeners.forEach(listener => listener());
  }
  start(): void {
    if (!this.stop) {
      // 先订阅宿主事件，再请求快照，避免启动期间漏掉 Tab 或会话变化。
      this.stop = this.host.observeShell(this.accept);
    }
  }
  accept = (value: ShellSnapshot): void => {
    if (
      value.revision < this.state.revision ||
      value.sessionEpoch < this.state.sessionEpoch
    ) {
      // 宿主事件与 Session 响应可能乱序；revision 和会话 epoch 都不得回退。
      return;
    }
    const changed = value.sessionEpoch !== this.state.sessionEpoch;
    if (changed) {
      this.logger.log('info', 'session', 'session.epoch.changed', {
        pageId: value.authenticated ? 'authenticated' : 'guest',
      });
    }
    this.state = {
      ...value,
      assetsVisible:
        changed || !value.authenticated ? false : this.state.assetsVisible,
    };
    if (changed) {
      this.clearSessionQueries();
    }
    this.emit();
  };
  /** Session Bridge 不携带 Shell revision，只更新同代或更新一代的会话字段。 */
  acceptSession(
    value: Pick<ShellSnapshot, 'authenticated' | 'sessionEpoch'>,
  ): void {
    if (value.sessionEpoch < this.state.sessionEpoch) return;
    this.accept({
      ...this.state,
      authenticated: value.authenticated,
      sessionEpoch: value.sessionEpoch,
    });
  }
  command = (command: Parameters<HostPort['command']>[0]): void => {
    this.logger.log('info', 'host', 'command.started', {
      pageId: command.type,
    });
    void this.host
      .command(command)
      .then(() =>
        this.logger.log('info', 'host', 'command.completed', {
          pageId: command.type,
        }),
      )
      .catch(() =>
        this.logger.log('error', 'host', 'command.failed', {
          code: 'HOST_COMMAND_FAILED',
          pageId: command.type,
        }),
      );
  };
  toggleVisibility = (): void => {
    if (!this.state.authenticated) {
      this.command({ type: 'authenticate' });
      return;
    }
    // 会话内同步更新，快速连点也基于最新值；不删除旧Native偏好键。
    this.state = { ...this.state, assetsVisible: !this.state.assetsVisible };
    this.emit();
    this.logger.log('info', 'privacy', 'visibility.toggled', {
      pageId: 'root-shell',
    });
  };
  dispose(): void {
    this.stop?.();
    this.stop = undefined;
  }
}
