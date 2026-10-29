// 文件职责：过滤字段并限制文本长度的Native日志入口。
import Foundation
import os

/// 白名单日志：只接收阶段和安全编号，不记录表单、响应或底层异常正文。
public enum AppLogger {
    /// 系统OSLog输出器，不依赖RN初始化。
    private static let logger = Logger(subsystem: "EntryTask", category: "Native")
    /// 允许输出的安全日志字段白名单。
    private static let allowed: Set<String> = ["eventId", "layer", "level", "module", "event", "timestamp", "apiId", "flowId", "requestId", "spanId", "parentSpanId", "transport", "durationMs", "code", "pageId", "epoch"]
    /// 只保留白名单字段，限制字符串长度后按级别写入OSLog。
    public static func log(_ event: String, _ fields: JSONObject = [:], error: Bool = false) {
        var safe = fields.filter { allowed.contains($0.key) }
        safe["event"] = event
        safe = safe.mapValues { value in
            if let text = value as? String {
                return String(text.prefix(160)).replacingOccurrences(of: "\\b[0-9]{11,19}\\b", with: "[REDACTED]", options: .regularExpression)
            }
            return value is NSNumber ? value : NSNull()
        }
        let line = (try? JSON.text(safe)) ?? event
        if error { logger.error("\(line, privacy: .public)") }
        else {
            #if DEBUG
            logger.notice("\(line, privacy: .public)")
            #else
            logger.info("\(line, privacy: .public)")
            #endif
        }
    }
}
