// 文件职责：纯签到状态转换，使用上海业务日并由外层事务提交。
import Foundation
import CoreKit

/// 纯状态转换：上海业务日、真实七日日历、3/7奖励；由外层事务提交。
public struct CheckinLedger {
    /// 当前上海业务日期，由外层时钟生成。
    private let today: String
    /// 响应模板，只提供展示结构，动态事实来自账本。
    private let template: JSONObject
    /// 上海时区公历，用于业务日计算。
    private var calendar: Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Shanghai")!; return c }
    /// 固定格式日期工具，避免设备区域设置影响业务日期。
    private var format: DateFormatter { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = calendar.timeZone; f.dateFormat = "yyyy-MM-dd"; return f }
    /// 捕获本次业务日期与响应结构，实际账本由调用方传入。
    public init(today: String, template: JSONObject) { self.today = today; self.template = template }
    /// 从历史日期计算连续天数与七日日历，查询本身不增加奖励。
    public func snapshot(_ state: JSONObject) throws -> JSONObject {
        let dates = Set(state["dates"] as? [String] ?? [])
        guard let date = format.date(from: today) else { throw AppFailure("MOCK_DATA_INVALID") }
        // 今天未签时从昨天回溯，保证尚未签到的早晨仍能展示已有连续记录。
        var cursor = dates.contains(today) ? date : calendar.date(byAdding: .day, value: -1, to: date)!
        var count = 0
        while dates.contains(format.string(from: cursor)) { count += 1; cursor = calendar.date(byAdding: .day, value: -1, to: cursor)! }
        let days = (-6...0).map { offset -> JSONObject in
            let value = format.string(from: calendar.date(byAdding: .day, value: offset, to: date)!)
            return ["date": value, "checked": dates.contains(value)]
        }
        var value = template
        value["calendar"] = days; value["businessDate"] = today; value["checkedToday"] = dates.contains(today)
        // 连续记录映射到七日奖励周期，但真实日历仍保留对应业务日期。
        value["consecutiveDays"] = count == 0 ? 0 : (count - 1) % 7 + 1
        value["energy"] = state["energy"] as? Int ?? 0; value["awardedEnergy"] = 0; value["alreadyChecked"] = false
        return value
    }
    /// 校验业务日期，已签到直接返回；首次签到更新日期、周期奖励和能量流水。
    public func submit(_ state: inout JSONObject, expectedDate: String) throws -> JSONObject {
        guard expectedDate == today else { throw AppFailure("BUSINESS_DATE_CHANGED") }
        var before = try snapshot(state)
        // 当天事实就是防重边界；重复提交返回已签到，不重复加奖励或流水。
        if before["checkedToday"] as? Bool == true { before["alreadyChecked"] = true; return before }
        var dates = state["dates"] as? [String] ?? []; dates.append(today); state["dates"] = dates
        let days = try snapshot(state)["consecutiveDays"] as? Int ?? 0
        let reward = days == 3 ? 30 : days == 7 ? 100 : 0
        state["energy"] = (state["energy"] as? Int ?? 0) + reward
        if reward > 0 {
            var records = state["energyRecords"] as? [JSONObject] ?? []
            records.append(["id": "energy-" + today, "title": "连续签到奖励", "summary": "+\(reward) 能量值", "date": today]); state["energyRecords"] = records
        }
        var result = try snapshot(state); result["awardedEnergy"] = reward; return result
    }
}
