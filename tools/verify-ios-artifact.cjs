#!/usr/bin/env node
'use strict';
// 验证真正的.app，而非只检查源码/生成目录。构建模式切换时复用同一命令。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const [artifact, mode, configuration] = process.argv.slice(2);
if (!artifact || !['native', 'rn'].includes(mode) || !['debug', 'release'].includes(configuration)) {
  throw new Error('Usage: node tools/verify-ios-artifact.cjs <EntryTask.app> <native|rn> <debug|release>');
}
const app = path.resolve(artifact);
const recorded = execFileSync('plutil', ['-extract', 'EntryTaskPageMode', 'raw', '-o', '-', path.join(app, 'Info.plist')], {encoding: 'utf8'}).trim();
if (recorded !== mode) throw new Error(`Artifact mode ${recorded} != ${mode}`);
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const jsonFiles = directory => fs.readdirSync(directory, {withFileTypes: true}).flatMap(entry => {
  const file = path.join(directory, entry.name);
  return entry.isDirectory() ? jsonFiles(file) : file.endsWith('.json') ? [file] : [];
});
const sources = ['contracts', 'mock'].flatMap(directory => jsonFiles(path.join(root, directory))).map(file => path.relative(root, file)).sort();
const index = JSON.parse(fs.readFileSync(path.join(app, 'runtime-resources.index.json'), 'utf8'));
if (JSON.stringify(Object.keys(index).sort()) !== JSON.stringify(sources)) throw new Error('Runtime resource paths differ from source');
for (const file of sources) {
  if (hash(path.join(root, file)) !== index[file] || hash(path.join(app, file)) !== index[file]) throw new Error(`Runtime hash mismatch: ${file}`);
}
const packed = ['contracts', 'mock'].flatMap(directory => jsonFiles(path.join(app, directory))).map(file => path.relative(app, file)).sort();
if (JSON.stringify(packed) !== JSON.stringify(sources)) throw new Error('Missing or stale packaged JSON');
const art = JSON.parse(fs.readFileSync(path.join(app, 'dashboard-art.index.json'), 'utf8'));
const names = Object.keys(art).sort();
if (names.length !== 13 || JSON.stringify(fs.readdirSync(path.join(app, 'dashboard-art')).sort()) !== JSON.stringify(names.map(name => name + '.png').sort())) throw new Error('Artwork paths/count mismatch');
for (const name of names) {
  if (hash(path.join(root, 'src/shared/assets', name + '.png')) !== art[name] || hash(path.join(app, 'dashboard-art', name + '.png')) !== art[name]) throw new Error(`Artwork hash mismatch: ${name}`);
}
const bundle = path.join(app, 'main.jsbundle');
if (mode === 'native' && fs.existsSync(bundle)) throw new Error('Native artifact retained a stale JS Bundle');
if (mode === 'rn' && configuration === 'release' && (!fs.existsSync(bundle) || fs.statSync(bundle).size === 0)) throw new Error('RN Release JS Bundle missing');
console.log(`iOS artifact OK: ${mode}/${configuration}, ${sources.length} JSON, ${names.length} PNG, JS Bundle ${fs.existsSync(bundle) ? fs.statSync(bundle).size + ' bytes' : 'absent'}`);
