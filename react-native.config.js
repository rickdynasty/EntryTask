// 官方run-ios/build-ios默认RN；Xcode工程默认native，显式构建参数优先。
const {commands} = require('@react-native-community/cli-platform-ios');
const {withPageMode} = require('./tools/ios-cli.cjs');
const {needsHeadless, runHeadless} = require('./tools/ios-headless.cjs');
const run = commands.find(command => command.name === 'run-ios');
const build = commands.find(command => command.name === 'build-ios');
module.exports = {
  commands: [
    withPageMode({
      ...run,
      func: (argv, context, args) =>
        needsHeadless(args)
          ? runHeadless(argv, context, args, build)
          : run.func(argv, context, args),
    }),
    withPageMode(build),
  ],
};
