#import <React-RCTAppDelegate/RCTDefaultReactNativeFactoryDelegate.h>
@class ETBridgeServices;

/// ObjC++工厂适配隔离C++协议，Swift通过此类保留原生依赖注入。
@interface ETFactoryDelegate : RCTDefaultReactNativeFactoryDelegate
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
