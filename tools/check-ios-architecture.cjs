#!/usr/bin/env node
'use strict';
// 检查真实Swift模块依赖；不将此脚本等同完整依赖图/并发验证。
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..', 'ios', 'Packages', 'EntryModules');
const modules = ['CoreKit', 'UserFeature', 'NetworkKit', 'StorageKit', 'DemoEngine', 'DashboardFeature'];
const errors = [];
function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(file) : file.endsWith('.swift') ? [file] : [];
  });
}
for (const module of modules) {
  for (const file of walk(path.join(root, 'Sources', module))) {
    const source = fs.readFileSync(file, 'utf8');
    const imports = [...source.matchAll(/^import\s+(\w+)/gm)].map(match => match[1]);
    for (const imported of imports) {
      if (modules.includes(imported) && imported !== 'CoreKit') errors.push(`${file}: illegal implementation import ${imported}`);
      if (imported.startsWith('React')) errors.push(`${file}: React belongs to the app target`);
      if (/CoreKit\/(Contracts|Foundation)\//.test(file) && ['UIKit', 'SwiftUI'].includes(imported)) errors.push(`${file}: UI imported by core contract`);
    }
  }
}
const manifest = fs.readFileSync(path.join(root, 'Package.swift'), 'utf8');
const targets = [...manifest.matchAll(/\.target\(name:\s*"([^"]+)"/g)].map(match => match[1]).sort();
if (JSON.stringify(targets) !== JSON.stringify([...modules].sort())) errors.push('Expected exactly six library targets');
if (errors.length) throw new Error(errors.join('\n'));
console.log('iOS architecture boundaries OK: App + six Swift libraries');
