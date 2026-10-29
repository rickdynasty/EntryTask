// 文件职责：业务内容构建模式；根Tab在两种模式下都由Native管理。
import Foundation

/// 构建产物中的模式是唯一来源，不通过运行时参数或UserDefaults覆盖。
enum PageMode: String { case native, rn }
/// 从安装产物读取并校验模式，禁止运行时静默切换。
enum BuildConfiguration {
    /// 从Info.plist读取的构建模式，非法值立即暴露配置错误。
    static let pageMode: PageMode = {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "EntryTaskPageMode") as? String,
              let mode = PageMode(rawValue: value) else { preconditionFailure("Missing or invalid ENTRYTASK_PAGE_MODE build setting") }
        return mode
    }()
}
