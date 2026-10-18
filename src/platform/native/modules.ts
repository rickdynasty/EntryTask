/** EntryTask RN：Native 能力适配层：封装 Codegen 模块、校验桥接数据并转换为稳定 Port。 */
// 只有平台适配层解析 Codegen 模块；缺少 iOS 实现时明确返回不可用。
export { default as hostModule } from '@specs/NativeHost';
export { default as networkModule } from '@specs/NativeNetwork';
export { default as storageModule } from '@specs/NativeStorage';
export { default as sessionModule } from '@specs/NativeSession';
export { default as telemetryModule } from '@specs/NativeTelemetry';
