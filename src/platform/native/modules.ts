// Only platform adapters can resolve Codegen modules. Missing iOS modules are explicit.
export { default as hostModule } from '@specs/NativeHost';
export { default as networkModule } from '@specs/NativeNetwork';
export { default as storageModule } from '@specs/NativeStorage';
export { default as sessionModule } from '@specs/NativeSession';
export { default as telemetryModule } from '@specs/NativeTelemetry';
