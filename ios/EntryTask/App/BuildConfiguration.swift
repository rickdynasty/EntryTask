import Foundation

/// 构建产物中的模式是唯一来源，不通过运行时参数或UserDefaults覆盖。
enum PageMode: String { case native, rn }
enum BuildConfiguration {
    static let pageMode: PageMode = {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "EntryTaskPageMode") as? String,
              let mode = PageMode(rawValue: value) else { preconditionFailure("Missing or invalid ENTRYTASK_PAGE_MODE build setting") }
        return mode
    }()
}
