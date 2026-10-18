// Logcat is independent of Metro and survives application process restarts.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT ||
  (process.platform === 'darwin' ? path.join(os.homedir(), 'Library/Android/sdk') : '');
const candidate = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const adb = fs.existsSync(candidate) ? candidate : 'adb';
const serial = process.argv[2];
const deviceArgs = serial ? ['-s', serial] : [];
const lookup = spawnSync(adb, [...deviceArgs, 'shell', 'pm', 'list', 'packages', '-U', 'com.entrytask'], {encoding: 'utf8'});
const uid = lookup.stdout?.match(/package:com\.entrytask\s+uid:(\d+)/)?.[1];
if (!uid) {
  console.error('无法找到已安装的 com.entrytask。请先运行 npx react-native run-android；检查 adb devices，多设备时指定 serial。');
  if (lookup.stderr) console.error(lookup.stderr.trim());
  process.exit(1);
}
console.log(`EntryTask Logcat (uid=${uid})：当前应用所有等级的 Native / RN 日志；Ctrl+C 退出。多设备：npm run logs:android -- <serial>`);
const child = spawn(adb, [
  ...deviceArgs, 'logcat', '--uid', uid, '-v', 'threadtime', '-T', '1', '*:V',
], {stdio: 'inherit'});
child.on('error', error => {
  console.error(`无法启动 adb (${error.code})，请配置 ANDROID_HOME 并运行 adb devices。`);
  process.exitCode = 1;
});
child.on('exit', code => { process.exitCode = code ?? 0; });
process.on('SIGINT', () => { child.kill('SIGINT'); });
process.on('SIGTERM', () => { child.kill('SIGTERM'); });
