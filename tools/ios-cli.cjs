'use strict';

// 使用CLI的项目命令扩展，仅在本次xcodebuild参数中选择模式；不改依赖、不写粘滞开关。
function withPageMode(command) {
  if (!command) throw new Error('Installed React Native CLI does not expose the iOS command');
  return {
    ...command,
    func(argv, context, args) {
      const extra = args.extraParams || [];
      const modes = extra.filter(value => value.startsWith('ENTRYTASK_PAGE_MODE='));
      if (modes.length > 1 || modes.some(value => !/^ENTRYTASK_PAGE_MODE=(rn|native)$/.test(value))) {
        throw new Error('ENTRYTASK_PAGE_MODE must be rn or native, specified once');
      }
      return command.func(argv, context, {
        ...args,
        extraParams: modes.length ? [...extra] : [...extra, 'ENTRYTASK_PAGE_MODE=rn'],
      });
    },
  };
}
module.exports = {withPageMode};
