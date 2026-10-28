const {withPageMode} = require('../tools/ios-cli.cjs');

describe('iOS CLI build mode', () => {
  test('official command defaults to RN without mutating caller arguments', () => {
    const func = jest.fn();
    const args = {extraParams: ['-quiet'], packager: false};
    withPageMode({name: 'run-ios', func}).func([], {}, args);
    expect(func.mock.calls[0][2]).toEqual({...args, extraParams: ['-quiet', 'ENTRYTASK_PAGE_MODE=rn']});
    expect(args.extraParams).toEqual(['-quiet']);
  });
  test('explicit mode is passed through', () => {
    const func = jest.fn();
    withPageMode({name: 'run-ios', func}).func([], {}, {extraParams: ['ENTRYTASK_PAGE_MODE=native']});
    expect(func.mock.calls[0][2].extraParams).toEqual(['ENTRYTASK_PAGE_MODE=native']);
  });
  test.each([['ENTRYTASK_PAGE_MODE=other'], ['ENTRYTASK_PAGE_MODE=rn', 'ENTRYTASK_PAGE_MODE=native']])('rejects invalid or conflicting mode %j', (...extra) => {
    expect(() => withPageMode({func: jest.fn()}).func([], {}, {extraParams: extra})).toThrow('ENTRYTASK_PAGE_MODE');
  });
});

const {selectSimulator} = require('../tools/ios-headless.cjs');
test('headless selection honors explicit simulator and rejects unavailable devices', () => {
  const devices = {runtime: [{name: 'iPhone Test', udid: 'test-id', isAvailable: true, state: 'Booted'}, {name: 'Unavailable', udid: 'invalid', isAvailable: false}]};
  expect(selectSimulator(devices, {}).udid).toBe('test-id');
  expect(selectSimulator(devices, {udid: 'test-id'}).name).toBe('iPhone Test');
  expect(() => selectSimulator(devices, {udid: 'invalid'})).toThrow('No matching');
});
