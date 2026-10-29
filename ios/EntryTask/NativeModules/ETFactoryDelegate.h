// 文件职责：对Swift公开的工厂声明，将React/C++实现隔离在ObjC++文件中。
#import <React-RCTAppDelegate/RCTDefaultReactNativeFactoryDelegate.h>
@class ETBridgeServices;

/// ObjC++工厂适配隔离C++协议，Swift通过此类保留原生依赖注入。
@interface ETFactoryDelegate : RCTDefaultReactNativeFactoryDelegate
// 注入受控Swift服务门面，五模块不各建一套平台依赖。
- (instancetype)initWithServices:(ETBridgeServices *)services;
@end
