#!/usr/bin/env node
'use strict';
// Xcode生成任务：共享JSON只有一份源码；校验失败不能沿用旧产物。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const output = path.resolve(process.argv[2] || '');
if (!output.endsWith('.app') || output === root) throw new Error('Expected generated .app resource directory');
execFileSync(process.execPath, [path.join(__dirname, 'validate-resources.cjs')], { cwd: root, stdio: 'inherit' });
const staging = path.join(output, '.entrytask-runtime');
fs.rmSync(staging, { recursive: true, force: true });
fs.mkdirSync(staging, { recursive: true });
const index = {};
const mode = process.env.ENTRYTASK_PAGE_MODE || 'native';
if (!['native', 'rn'].includes(mode)) throw new Error('ENTRYTASK_PAGE_MODE must be native or rn');
function copy(directory, relative) {
  const source = path.join(root, directory, relative);
  const target = path.join(staging, directory, relative);
  const stat = fs.lstatSync(source);
  if (stat.isSymbolicLink()) throw new Error('Runtime resource symlinks are not allowed');
  if (stat.isDirectory()) {
    fs.mkdirSync(target, { recursive: true });
    for (const name of fs.readdirSync(source).sort()) copy(directory, path.join(relative, name));
  } else if (source.endsWith('.json')) {
    fs.copyFileSync(source, target);
    index[path.posix.join(directory, relative.split(path.sep).join('/'))] = crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex');
  }
}
try {
  for (const directory of ['contracts', 'mock']) copy(directory, '');
  for (const directory of ['contracts', 'mock']) {
    fs.rmSync(path.join(output, directory), { recursive: true, force: true });
    fs.renameSync(path.join(staging, directory), path.join(output, directory));
  }
  // 共用素材只有RN assets一份源码；每次替换Native生成目录，防止删除后仍打包旧图。
  const art = path.join(staging, 'dashboard-art');
  fs.mkdirSync(art, {recursive: true});
  const names = ['hero-ginkgo', 'certification-gift', 'seasonal-gifts', 'custom-cards', 'crown', 'book', 'profile-avatar', 'advisor-avatar', 'credit-gift', 'quick-0', 'quick-1', 'quick-2', 'quick-3'];
  const artwork = {};
  for (const name of names) {
    const source = path.join(root, 'src/shared/assets', name + '.png');
    fs.copyFileSync(source, path.join(art, name + '.png'));
    artwork[name] = crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex');
  }
  fs.rmSync(path.join(output, 'dashboard-art'), {recursive: true, force: true});
  fs.renameSync(art, path.join(output, 'dashboard-art'));
  fs.writeFileSync(path.join(output, 'dashboard-art.index.json'), JSON.stringify(artwork, null, 2) + '\n');
  console.log(`EntryTask page mode: ${mode}; shared artwork: ${names.length}`);
  fs.writeFileSync(path.join(output, 'runtime-resources.index.json'), JSON.stringify(index, null, 2) + '\n');
} finally {
  fs.rmSync(staging, { recursive: true, force: true });
}
console.log(`iOS runtime resources: ${Object.keys(index).length} JSON files`);
