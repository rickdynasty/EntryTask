#import "ETFactoryDelegate.h"
#import <EntryTaskSpecs/EntryTaskSpecs.h>
#import "EntryTask-Swift.h"
#import <React/RCTInvalidating.h>
#include <atomic>

// Host只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeHostAdapter : NativeHostSpecBase <NativeHostSpec, RCTInvalidating>
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeHostAdapter {
    ETBridgeServices *_services;
    NSString *_owner;
    std::atomic_bool _valid;
}
+ (NSString *)moduleName { return @"NativeHost"; }
+ (BOOL)requiresMainQueueSetup { return NO; }
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
        __weak ETNativeHostAdapter *weakSelf = self;
        [services observe:_owner handler:^(NSString *event) {
            ETNativeHostAdapter *strongSelf = weakSelf;
            if (strongSelf && strongSelf->_valid && strongSelf->_eventEmitterCallback) [strongSelf emitOnHostEvent:event];
        }];
    }
    return self;
}
- (void)bootstrap:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeHost" method:@"bootstrap" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (void)command:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeHost" method:@"command" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeHostSpecJSI>(params);
}
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
- (void)dealloc { [self invalidate]; }
@end

// Network只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeNetworkAdapter : NativeNetworkSpecBase <NativeNetworkSpec, RCTInvalidating>
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeNetworkAdapter {
    ETBridgeServices *_services;
    NSString *_owner;
    std::atomic_bool _valid;
}
+ (NSString *)moduleName { return @"NativeNetwork"; }
+ (BOOL)requiresMainQueueSetup { return NO; }
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
- (void)execute:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeNetwork" method:@"execute" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (void)cancel:(NSString *)requestId { if (_valid) [_services cancel:requestId]; }
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeNetworkSpecJSI>(params);
}
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
- (void)dealloc { [self invalidate]; }
@end

// Session只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeSessionAdapter : NativeSessionSpecBase <NativeSessionSpec, RCTInvalidating>
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeSessionAdapter {
    ETBridgeServices *_services;
    NSString *_owner;
    std::atomic_bool _valid;
}
+ (NSString *)moduleName { return @"NativeSession"; }
+ (BOOL)requiresMainQueueSetup { return NO; }
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
- (void)getSnapshot:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeSession" method:@"getSnapshot" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeSessionSpecJSI>(params);
}
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
- (void)dealloc { [self invalidate]; }
@end

// Storage只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeStorageAdapter : NativeStorageSpecBase <NativeStorageSpec, RCTInvalidating>
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeStorageAdapter {
    ETBridgeServices *_services;
    NSString *_owner;
    std::atomic_bool _valid;
}
+ (NSString *)moduleName { return @"NativeStorage"; }
+ (BOOL)requiresMainQueueSetup { return NO; }
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
- (void)read:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeStorage" method:@"read" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (void)write:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeStorage" method:@"write" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (void)remove:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeStorage" method:@"remove" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeStorageSpecJSI>(params);
}
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
- (void)dealloc { [self invalidate]; }
@end

// Telemetry只转发薄传输参数；业务逻辑位于Swift facade及各库。
@interface ETNativeTelemetryAdapter : NativeTelemetrySpecBase <NativeTelemetrySpec, RCTInvalidating>
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
@implementation ETNativeTelemetryAdapter {
    ETBridgeServices *_services;
    NSString *_owner;
    std::atomic_bool _valid;
}
+ (NSString *)moduleName { return @"NativeTelemetry"; }
+ (BOOL)requiresMainQueueSetup { return NO; }
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) {
        _services = services; _owner = NSUUID.UUID.UUIDString; _valid = true; [services activate:_owner];
    }
    return self;
}
- (void)emitBatch:(NSString *)input resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    if (!_valid) return;
    [_services perform:@"NativeTelemetry" method:@"emitBatch" input:input owner:_owner completion:^(NSString *reply) { resolve(reply); }];
}
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params {
    return std::make_shared<facebook::react::NativeTelemetrySpecJSI>(params);
}
- (void)invalidate { if (_valid.exchange(false)) [_services invalidate:_owner]; }
- (void)dealloc { [self invalidate]; }
@end

@implementation ETFactoryDelegate {
    ETBridgeServices *_services;
}
- (instancetype)initWithServices:(ETBridgeServices *)services {
    if ((self = [super init])) _services = services;
    return self;
}
// RN 0.86按类查找后调用实例工厂；此处注入服务，不依赖生成provider的无参new。
- (Class)getModuleClassFromName:(const char *)name {
    NSString *module = [NSString stringWithUTF8String:name];
    if ([module isEqualToString:@"NativeHost"]) return ETNativeHostAdapter.class;
    if ([module isEqualToString:@"NativeNetwork"]) return ETNativeNetworkAdapter.class;
    if ([module isEqualToString:@"NativeSession"]) return ETNativeSessionAdapter.class;
    if ([module isEqualToString:@"NativeStorage"]) return ETNativeStorageAdapter.class;
    if ([module isEqualToString:@"NativeTelemetry"]) return ETNativeTelemetryAdapter.class;
    return [super getModuleClassFromName:name];
}
- (id<RCTTurboModule>)getModuleInstanceFromClass:(Class)moduleClass {
    if (moduleClass == ETNativeHostAdapter.class) return [[ETNativeHostAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeNetworkAdapter.class) return [[ETNativeNetworkAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeSessionAdapter.class) return [[ETNativeSessionAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeStorageAdapter.class) return [[ETNativeStorageAdapter alloc] initWithServices:_services];
    if (moduleClass == ETNativeTelemetryAdapter.class) return [[ETNativeTelemetryAdapter alloc] initWithServices:_services];
    return [super getModuleInstanceFromClass:moduleClass];
}
@end
