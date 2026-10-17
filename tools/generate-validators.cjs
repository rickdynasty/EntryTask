const fs = require('node:fs');
const path = require('node:path');
const Ajv = require('ajv');
const standaloneCode = require('ajv/dist/standalone').default;
const root = path.resolve(__dirname, '..');
const ajv = new Ajv({code: {source: true}, allErrors: true, strict: true});
const catalog = require('../contracts/api/endpoints.json');
const exportsMap = {};
for (const endpoint of catalog.endpoints) {
  const schema = JSON.parse(fs.readFileSync(path.join(root, 'contracts/api', endpoint.responseSchema), 'utf8'));
  ajv.addSchema(schema);
  exportsMap[endpoint.apiId.replaceAll('.', '_')] = schema.$id;
}
const target = path.join(root, 'src/infrastructure/network/generated');
fs.mkdirSync(target, {recursive: true});
const source = '/* eslint-disable */\n// Generated from contracts/api/*.schema.json; run npm run contracts:generate.\n' + standaloneCode(ajv, exportsMap);
fs.writeFileSync(path.join(target, 'validators.js'), source);
fs.writeFileSync(path.join(target, 'validators.d.ts'), Object.keys(exportsMap).map(name => `export function ${name}(data: unknown): boolean;`).join('\n') + '\n');
