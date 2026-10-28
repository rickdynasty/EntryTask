import Foundation
import CoreKit

/// 纯状态转换：上海业务日、真实七日日历、3/7奖励；由外层事务提交。
public struct CheckinLedger {
    private let today: String
    private let template: JSONObject
    private var calendar: Calendar { var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "Asia/Shanghai")!; return c }
    private var format: DateFormatter { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = calendar.timeZone; f.dateFormat = "yyyy-MM-dd"; return f }
    public init(today: String, template: JSONObject) { self.today = today; self.template = template }
    public func snapshot(_ state: JSONObject) throws -> JSONObject {
        let dates = Set(state["dates"] as? [String] ?? [])
        guard let date = format.date(from: today) else { throw AppFailure("MOCK_DATA_INVALID") }
        var cursor = dates.contains(today) ? date : calendar.date(byAdding: .day, value: -1, to: date)!
        var count = 0
        while dates.contains(format.string(from: cursor)) { count += 1; cursor = calendar.date(byAdding: .day, value: -1, to: cursor)! }
        let days = (-6...0).map { offset -> JSONObject in
            let value = format.string(from: calendar.date(byAdding: .day, value: offset, to: date)!)
            return ["date": value, "checked": dates.contains(value)]
        }
        var value = template
        value["calendar"] = days; value["businessDate"] = today; value["checkedToday"] = dates.contains(today)
        value["consecutiveDays"] = count == 0 ? 0 : (count - 1) % 7 + 1
        value["energy"] = state["energy"] as? Int ?? 0; value["awardedEnergy"] = 0; value["alreadyChecked"] = false
        return value
    }
    public func submit(_ state: inout JSONObject, expectedDate: String) throws -> JSONObject {
        guard expectedDate == today else { throw AppFailure("BUSINESS_DATE_CHANGED") }
        var before = try snapshot(state)
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
