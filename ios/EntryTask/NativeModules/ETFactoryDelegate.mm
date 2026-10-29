// 文件职责：五个Codegen薄适配器及RN工厂依赖注入；业务逻辑交由Swift服务实现。
#import "ETFactoryDelegate.h"
#import <EntryTaskSpecs/EntryTaskSpecs.h>
#import "EntryTask-Swift.h"
#import <React/RCTInvalidating.h>
#include <atomic>

// Host只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeHostAdapter : NativeHostSpecBase <NativeHostSpec, RCTInvalidating>
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeHostAdapter {
    // 共用Swift服务门面，所有adapter复用同一Native应用图。
    ETBridgeServices *_services;
    // 当前模块实例的生命周期编号，用于任务与监听清理。
    NSString *_owner;
    // 原子失效标记，销毁/Reload后禁止继续转发调用。
    std::atomic_bool _valid;
}
// 模块名与Codegen契约一致，不能随类名整理改动。
+ (NSString *)moduleName { return @"NativeHost"; }
// 初始化无需占用主队列；UI动作由Swift边界切到MainActor。
+ (BOOL)requiresMainQueueSetup { return NO; }
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
        __weak ETNativeHostAdapter *weakSelf = self;
        [services observe:_owner handler:^(NSString *event) {
            ETNativeHostAdapter *strongSelf = weakSelf;
            // 弱引用避免监听环；事件发出前同时检查实例、失效标记与JS监听入口。
            if (strongSelf && strongSelf->_valid && strongSelf->_eventEmitterCallback) [strongSelf emitOnHostEvent:event];
        }];
    }
    return self;
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)bootstrap:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeHost" method:@"bootstrap" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)command:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeHost" method:@"command" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 创建对应Codegen生成的JSI绑定，不手写第二套JS接口。
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeHostSpecJSI>(params);
}
// 原子地失效一次，取消owner任务并注销监听；允许重复调用。
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
// 释放时兜底失效，避免运行时销毁后留下后台任务。
- (void)dealloc { [self invalidate]; }
@end

// Network只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeNetworkAdapter : NativeNetworkSpecBase <NativeNetworkSpec, RCTInvalidating>
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeNetworkAdapter {
    // 共用Swift服务门面，所有adapter复用同一Native应用图。
    ETBridgeServices *_services;
    // 当前模块实例的生命周期编号，用于任务与监听清理。
    NSString *_owner;
    // 原子失效标记，销毁/Reload后禁止继续转发调用。
    std::atomic_bool _valid;
}
// 模块名与Codegen契约一致，不能随类名整理改动。
+ (NSString *)moduleName { return @"NativeNetwork"; }
// 初始化无需占用主队列；UI动作由Swift边界切到MainActor。
+ (BOOL)requiresMainQueueSetup { return NO; }
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)execute:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeNetwork" method:@"execute" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 转发独立网络取消，不等待原请求Promise完成。
- (void)cancel:(NSString *)requestId { if (_valid) [_services cancel:requestId]; }
// 创建对应Codegen生成的JSI绑定，不手写第二套JS接口。
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeNetworkSpecJSI>(params);
}
// 原子地失效一次，取消owner任务并注销监听；允许重复调用。
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
// 释放时兜底失效，避免运行时销毁后留下后台任务。
- (void)dealloc { [self invalidate]; }
@end

// Session只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeSessionAdapter : NativeSessionSpecBase <NativeSessionSpec, RCTInvalidating>
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeSessionAdapter {
    // 共用Swift服务门面，所有adapter复用同一Native应用图。
    ETBridgeServices *_services;
    // 当前模块实例的生命周期编号，用于任务与监听清理。
    NSString *_owner;
    // 原子失效标记，销毁/Reload后禁止继续转发调用。
    std::atomic_bool _valid;
}
// 模块名与Codegen契约一致，不能随类名整理改动。
+ (NSString *)moduleName { return @"NativeSession"; }
// 初始化无需占用主队列；UI动作由Swift边界切到MainActor。
+ (BOOL)requiresMainQueueSetup { return NO; }
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)getSnapshot:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeSession" method:@"getSnapshot" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 创建对应Codegen生成的JSI绑定，不手写第二套JS接口。
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeSessionSpecJSI>(params);
}
// 原子地失效一次，取消owner任务并注销监听；允许重复调用。
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
// 释放时兜底失效，避免运行时销毁后留下后台任务。
- (void)dealloc { [self invalidate]; }
@end

// Storage只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeStorageAdapter : NativeStorageSpecBase <NativeStorageSpec, RCTInvalidating>
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeStorageAdapter {
    // 共用Swift服务门面，所有adapter复用同一Native应用图。
    ETBridgeServices *_services;
    // 当前模块实例的生命周期编号，用于任务与监听清理。
    NSString *_owner;
    // 原子失效标记，销毁/Reload后禁止继续转发调用。
    std::atomic_bool _valid;
}
// 模块名与Codegen契约一致，不能随类名整理改动。
+ (NSString *)moduleName { return @"NativeStorage"; }
// 初始化无需占用主队列；UI动作由Swift边界切到MainActor。
+ (BOOL)requiresMainQueueSetup { return NO; }
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)read:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeStorage" method:@"read" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)write:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeStorage" method:@"write" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)remove:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeStorage" method:@"remove" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 创建对应Codegen生成的JSI绑定，不手写第二套JS接口。
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeStorageSpecJSI>(params);
}
// 原子地失效一次，取消owner任务并注销监听；允许重复调用。
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
// 释放时兜底失效，避免运行时销毁后留下后台任务。
- (void)dealloc { [self invalidate]; }
@end

// Telemetry只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeTelemetryAdapter : NativeTelemetrySpecBase <NativeTelemetrySpec, RCTInvalidating>
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeTelemetryAdapter {
    // 共用Swift服务门面，所有adapter复用同一Native应用图。
    ETBridgeServices *_services;
    // 当前模块实例的生命周期编号，用于任务与监听清理。
    NSString *_owner;
    // 原子失效标记，销毁/Reload后禁止继续转发调用。
    std::atomic_bool _valid;
}
// 模块名与Codegen契约一致，不能随类名整理改动。
+ (NSString *)moduleName { return @"NativeTelemetry"; }
// 初始化无需占用主队列；UI动作由Swift边界切到MainActor。
+ (BOOL)requiresMainQueueSetup { return NO; }
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
// 有效实例才转发薄参数；结果使用统一JSON信封resolve，业务失败不重复完成Promise。
- (void)emitBatch:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeTelemetry" method:@"emitBatch" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
// 创建对应Codegen生成的JSI绑定，不手写第二套JS接口。
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeTelemetrySpecJSI>(params);
}
// 原子地失效一次，取消owner任务并注销监听；允许重复调用。
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
// 释放时兜底失效，避免运行时销毁后留下后台任务。
- (void)dealloc { [self invalidate]; }
@end

// 工厂负责识别模块类与构造注入实例，不能只提供无参provider来假定已完成注册。
@implementation ETFactoryDelegate {
    // 共用Swift服务门面，所有adapter复用同一Native应用图。
    ETBridgeServices *_services;
}
// 注入共用服务；模块adapter为自身注册owner，工厂仅保留服务。
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) _services = services;
    return self;
}
// RN 0.86按类查找后调用实例工厂；此处注入服务，不依赖生成provider的无参new。
// 只识别本工程五模块；其他RN模块仍交回父类解析。
- (Class)getModuleClassFromName:(const char *)name {
    NSString *module = [NSString stringWithUTF8String:name];
    if ([module isEqualToString:@"NativeHost"]) return ETNativeHostAdapter.class;
    if ([module isEqualToString:@"NativeNetwork"]) return ETNativeNetworkAdapter.class;
    if ([module isEqualToString:@"NativeSession"]) return ETNativeSessionAdapter.class;
    if ([module isEqualToString:@"NativeStorage"]) return ETNativeStorageAdapter.class;
    if ([module isEqualToString:@"NativeTelemetry"]) return ETNativeTelemetryAdapter.class;
    return [super getModuleClassFromName:name];
}
// 按实际模块类注入服务实例，未知类保持RN默认构造路径。
- (id<RCTTurboModule>)getModuleInstanceFromClass:(Class)moduleClass {
    if (moduleClass == ETNativeHostAdapter.class) return [[ETNativeHostAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeNetworkAdapter.class) return [[ETNativeNetworkAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeSessionAdapter.class) return [[ETNativeSessionAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeStorageAdapter.class) return [[ETNativeStorageAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeTelemetryAdapter.class) return [[ETNativeTelemetryAdapter alloc] initWithServices:_services];
    return [super getModuleInstanceFromClass:moduleClass];
}
@end
