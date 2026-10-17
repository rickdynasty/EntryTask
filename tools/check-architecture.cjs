const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const violations = [];
function visit(dir) {
  for (const entry of fs.readdirSync(path.join(root, dir), {withFileTypes: true})) {
    const file = `${dir}/${entry.name}`;
    if (entry.isDirectory()) { visit(file); continue; }
    if (!/\.[jt]sx?$/.test(file) || file.includes('/generated/')) continue;
    const text = fs.readFileSync(path.join(root, file), 'utf8');
    const imports = [...text.matchAll(/(?:from\s*|require\(\s*)['"]([^'"]+)['"]/g)].map(m => m[1]);
    const fail = reason => violations.push(`${file}: ${reason}`);
    if (imports.some(i => i.includes('specs/')) && !file.startsWith('src/platform/native/')) fail('Native specs outside platform adapter');
    if (imports.includes('axios') && !file.endsWith('/RnHttpTransport.ts')) fail('Axios outside RN transport');
    if (file.includes('/domain/') && imports.some(i => /^(react|@reduxjs|react-redux|@infra|@specs)/.test(i))) fail('Domain depends on implementation');
    if (/src\/features\/[^/]+\/presentation\//.test(file) && imports.some(i => /axios|@specs|@platform\/native|\/data\//.test(i))) fail('View/VM directly depends on IO');
    if (/\bfetch\s*\(|new XMLHttpRequest/.test(text)) fail('Unwrapped HTTP');
    if (imports.some(i => /AsyncStorage|\/mock\//i.test(i))) fail('JS persistence or direct Mock data');
  }
}
visit('src');
if (fs.existsSync(path.join(root, 'index.js'))) violations.push('Root index.js must not remain');
if (violations.length) { console.error(violations.join('\n')); process.exit(1); }
console.log('Architecture boundaries OK');
