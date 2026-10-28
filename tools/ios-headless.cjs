'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');

// Xcode部分安装只提供Device Hub/CoreSimulator，官方CLI仍尝试打开Simulator.app。
// 仅该环境下的Debug模拟器走此入口，构建继续委托官方build-ios，不修改node_modules。
function needsHeadless(args) {
  if (process.platform !== 'darwin' || args.device || args.interactive || (args.mode && args.mode !== 'Debug')) return false;
  const developer = execFileSync('xcode-select', ['-p'], {encoding: 'utf8'}).trim();
  return !fs.existsSync(path.join(developer, 'Applications/Simulator.app'));
}
function selectSimulator(devices, args) {
  const available = Object.values(devices).flat().filter(device => device.isAvailable);
  const device = args.udid
    ? available.find(item => item.udid === args.udid)
    : args.simulator
      ? available.find(item => item.name === args.simulator)
      : available.find(item => item.state === 'Booted' && item.name.startsWith('iPhone')) || available.find(item => item.name.startsWith('iPhone'));
  if (!device) throw new Error('No matching iOS Simulator. Check xcrun simctl list devices available.');
  return device;
}
async function runHeadless(argv, context, args, build) {
  const {logger, findDevServerPort, startServerInNewWindow} = require('@react-native-community/cli-tools');
  const device = selectSimulator(JSON.parse(execFileSync('xcrun', ['simctl', 'list', 'devices', 'available', '-j'], {encoding: 'utf8'})).devices, args);
  logger.info('Simulator.app unavailable; using CoreSimulator with the official Debug builder.');
  if (args.packager !== false) {
    const server = await findDevServerPort(args.port || 8081, context.root);
    args = {...args, port: server.port};
    if (server.startPackager) await startServerInNewWindow(server.port, context.root, context.reactNativePath, args.terminal);
  }
  if (device.state !== 'Booted') execFileSync('xcrun', ['simctl', 'boot', device.udid], {stdio: 'inherit'});
  execFileSync('xcrun', ['simctl', 'bootstatus', device.udid, '-b'], {stdio: 'inherit'});
  const architecture = process.arch === 'arm64' ? 'arm64' : 'x86_64';
  const previous = process.cwd();
  try {
    let app;
    if (args.binaryPath) {
      app = path.resolve(context.root, args.binaryPath);
    } else {
      await build.func(argv, context, {...args, mode: 'Debug', destination: undefined, extraParams: [...args.extraParams, `ARCHS=${architecture}`, 'ONLY_ACTIVE_ARCH=YES']});
      const project = context.project.ios.xcodeProject;
      const flags = [project.isWorkspace ? '-workspace' : '-project', project.name, '-scheme', args.scheme || 'EntryTask', '-configuration', 'Debug', '-sdk', 'iphonesimulator', '-destination', 'id=' + device.udid, ...(args.buildFolder ? ['-derivedDataPath', args.buildFolder] : []), ...(args.xcconfig ? ['-xcconfig', args.xcconfig] : []), ...args.extraParams, '-showBuildSettings', '-json'];
      const settings = JSON.parse(execFileSync('xcodebuild', flags, {cwd: context.project.ios.sourceDir, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024}));
      const target = settings.find(item => item.target === (args.target || 'EntryTask'));
      if (!target) throw new Error('App target build settings missing');
      app = path.join(target.buildSettings.TARGET_BUILD_DIR, target.buildSettings.FULL_PRODUCT_NAME);
    }
    const recordedMode = execFileSync('plutil', ['-extract', 'EntryTaskPageMode', 'raw', '-o', '-', path.join(app, 'Info.plist')], {encoding: 'utf8'}).trim();
    const expectedMode = args.extraParams.find(value => value.startsWith('ENTRYTASK_PAGE_MODE=')).split('=')[1];
    if (recordedMode !== expectedMode) throw new Error('Built artifact page mode does not match requested mode');
    const bundleID = execFileSync('plutil', ['-extract', 'CFBundleIdentifier', 'raw', '-o', '-', path.join(app, 'Info.plist')], {encoding: 'utf8'}).trim();
    execFileSync('xcrun', ['simctl', 'install', device.udid, app], {stdio: 'inherit'});
    execFileSync('xcrun', ['simctl', 'launch', device.udid, bundleID], {stdio: 'inherit'});
    logger.success(`Launched ${bundleID} on ${device.name}`);
  } finally { process.chdir(previous); }
}
module.exports = {needsHeadless, selectSimulator, runHeadless};
