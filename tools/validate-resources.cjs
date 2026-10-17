const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const read = p => JSON.parse(fs.readFileSync(path.join(root, p), 'utf8'));
const manifest = read('mock/manifest.json');
const endpoints = read('contracts/api/endpoints.json').endpoints;
const ids = new Set();
const routes = new Set();
for (const route of manifest.routes) {
  if (ids.has(route.apiId) || routes.has(`${route.method} ${route.path}`)) throw new Error('Duplicate Mock route');
  ids.add(route.apiId); routes.add(`${route.method} ${route.path}`);
  if (route.response.includes('..') || path.isAbsolute(route.response)) throw new Error('Unsafe fixture path');
  const body = read(`mock/${route.response}`);
  if (body.code !== 200 || !Object.hasOwn(body, 'data')) throw new Error(`Invalid response: ${route.apiId}`);
}
for (const endpoint of endpoints) {
  const route = manifest.routes.find(r => r.apiId === endpoint.apiId);
  if (!route || route.method !== endpoint.method || route.path !== endpoint.path) throw new Error(`Missing or inconsistent Mock: ${endpoint.apiId}`);
  const validate = require('../src/infrastructure/network/generated/validators.js')[endpoint.apiId.replaceAll('.', '_')];
  if (!validate || !validate(read(`mock/${route.response}`).data)) throw new Error(`Schema mismatch: ${endpoint.apiId}`);
}
function scan(dir) {
  return fs.readdirSync(path.join(root, dir), {withFileTypes: true}).flatMap(e => e.isDirectory() ? scan(`${dir}/${e.name}`) : [`${dir}/${e.name}`]);
}
const files = [...scan('contracts'), ...scan('mock')].filter(p => p.endsWith('.json')).sort();
const hash = crypto.createHash('sha256');
for (const file of files) { read(file); hash.update(file); hash.update(fs.readFileSync(path.join(root, file))); }
console.log(`Runtime resources OK: ${manifest.routes.length} native routes, ${endpoints.length} RN endpoints, sha256=${hash.digest('hex')}`);
