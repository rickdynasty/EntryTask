import { EndpointRegistry } from '@infra/network/EndpointRegistry';
import { TransportRouter } from '@infra/network/TransportRouter';
import type { EndpointPolicy, Transport } from '@platform/ports/NetworkPort';
import {
  home_content,
  product_list,
} from '@infra/network/generated/validators';

const logger = { log: jest.fn() };
const native: Transport = { execute: jest.fn() };
const http: Transport = { execute: jest.fn() };
const policy: EndpointPolicy = {
  apiId: 'test.content',
  method: 'GET',
  path: '/content',
  transport: 'rnHttp',
  authScope: 'none',
  baseUrl: 'https://content.example.test',
  timeoutMs: 10000,
  policyRevision: 1,
};
afterEach(() => jest.clearAllMocks());

test('explicit RN HTTP policy calls only RN transport and decodes its envelope', async () => {
  jest
    .mocked(http.execute)
    .mockResolvedValue({
      httpStatus: 200,
      bodyText: JSON.stringify({ code: 200, data: { title: 'content' } }),
    });
  const router = new TransportRouter(
    new EndpointRegistry([policy]),
    { rnHttp: http, nativeMock: native, native },
    logger,
  );
  await expect(
    router.execute({ apiId: policy.apiId }, new AbortController().signal),
  ).resolves.toEqual({ title: 'content' });
  expect(http.execute).toHaveBeenCalledTimes(1);
  expect(native.execute).not.toHaveBeenCalled();
});

test('transport failure never falls back; independent HTTP 401 remains an HTTP error', async () => {
  jest
    .mocked(http.execute)
    .mockResolvedValue({ httpStatus: 401, bodyText: '{}' });
  const router = new TransportRouter(
    new EndpointRegistry([policy]),
    { rnHttp: http, nativeMock: native, native },
    logger,
  );
  await expect(
    router.execute({ apiId: policy.apiId }, new AbortController().signal),
  ).rejects.toMatchObject({ code: 'HTTP_401' });
  expect(native.execute).not.toHaveBeenCalled();
});

test('RN bank credentials and unconfigured endpoints are rejected', () => {
  expect(() =>
    new EndpointRegistry([{ ...policy, authScope: 'nativeBank' }]).resolve(
      policy.apiId,
    ),
  ).toThrow();
  expect(() => new EndpointRegistry([]).resolve('unknown')).toThrow();
});

test('generated runtime validators reject missing fields, null data and invalid pagination', () => {
  expect(home_content(null)).toBe(false);
  expect(home_content({ title: 'x' })).toBe(false);
  expect(
    product_list({
      items: [],
      page: 0,
      pageSize: 10,
      total: 0,
      hasMore: false,
    }),
  ).toBe(false);
});
