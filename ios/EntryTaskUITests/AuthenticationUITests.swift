// 文件职责：用独立测试存储验证认证及两种模式的四页主链路。
import XCTest

/// 使用独立测试存储目录；不清理或覆盖用户的演示账号。
final class AuthenticationUITests: XCTestCase {
    /// 启动使用独立存储目录的UI测试应用，不清除正式账号。
    private func app() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["--entrytask-ui-test"]
        app.launchEnvironment["ENTRYTASK_TEST_ID"] = UUID().uuidString
        app.launch()
        XCTAssertTrue(app.textFields["auth.username"].waitForExistence(timeout: 15))
        return app
    }
    /// 设备验证启动先认证、两表单账号隔离及密码离页清除。
    func testNativeAuthenticationAppearsBeforeBusinessAndFormsAreIndependent() {
        let app = app()
        XCTAssertFalse(app.tabBars.buttons["财富"].exists)
        app.textFields["auth.username"].tap(); app.textFields["auth.username"].typeText("synthetic-input")
        app.secureTextFields["auth.password"].tap(); app.secureTextFields["auth.password"].typeText("synthetic-secret")
        app.buttons["auth.switch"].tap()
        XCTAssertTrue(app.staticTexts["注册新账号"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.textFields["auth.username"].value as? String, "请输入用户名")
        XCTAssertEqual(app.secureTextFields["auth.password"].value as? String, "请输入密码")
        app.buttons["auth.switch"].tap()
        XCTAssertEqual(app.textFields["auth.username"].value as? String, "synthetic-input")
        XCTAssertEqual(app.secureTextFields["auth.password"].value as? String, "请输入密码")
    }
    /// 设备验证当前模式四页、真实签到及Native隐私与重新认证流程。
    func testRegistrationReturnsToSelectedDashboardAndFourTabs() throws {
        let app = app()
        app.buttons["auth.switch"].tap()
        let username = "ui-" + UUID().uuidString.prefix(8)
        app.textFields["auth.username"].tap(); app.textFields["auth.username"].typeText(String(username))
        app.secureTextFields["auth.password"].tap(); app.secureTextFields["auth.password"].typeText("Synthetic-Only-123")
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.tabBars.buttons["财富"].waitForExistence(timeout: 30))
        XCTAssertFalse(app.buttons["auth.submit"].exists)
        // 从测试产物读取构建模式，断言真正挂载的业务根，不能以页面标题猜测模式。
        let mode = try XCTUnwrap(Bundle(for: type(of: self)).object(forInfoDictionaryKey: "EntryTaskPageMode") as? String)
        XCTAssertTrue(app.otherElements[mode == "rn" ? "dashboard.rn" : "dashboard.native"].waitForExistence(timeout: 30))
        let search = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.otherElements["搜索"].exists || app.buttons["搜索"].exists || app.staticTexts["搜索"].exists
        }, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [search], timeout: 45), .completed)
        // 根Tab由UIKit驱动；两种业务内容都必须接受同一选择并展示对应区域。
        app.tabBars.buttons["财富"].tap()
        XCTAssertTrue(app.tabBars.buttons["财富"].isSelected)
        XCTAssertTrue(app.staticTexts["总资产（元）"].waitForExistence(timeout: 20))
        if mode == "native" {
            let privacy = app.buttons["dashboard.privacy"]
            XCTAssertTrue(privacy.waitForExistence(timeout: 15)); privacy.tap()
            XCTAssertEqual(privacy.label, "隐藏资产")
        }
        attachment(app, "wealth-" + mode)
        // 滚动找到真实签到动作，等待可提交后验证服务端写入事实。
        app.tabBars.buttons["会员"].tap()
        let checkin = app.buttons["立即签到"]
        for _ in 0..<3 {
            if checkin.exists && checkin.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(checkin.waitForExistence(timeout: 20))
        XCTAssertTrue(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: checkin)], timeout: 20) == .completed)
        checkin.tap()
        XCTAssertTrue(app.buttons["今日已签到"].waitForExistence(timeout: 25))
        attachment(app, "member-" + mode)
        // 切入我的页面核对收支展示；Native额外验证同一会话的隐私同步。
        app.tabBars.buttons["我的"].tap()
        if mode == "native" {
            let privacy = app.buttons["dashboard.privacy"]
            XCTAssertTrue(privacy.waitForExistence(timeout: 15)); XCTAssertEqual(privacy.label, "隐藏资产")
        }
        let cashflow = app.staticTexts["月度收支"]
        for _ in 0..<3 {
            if cashflow.exists { break }
            app.swipeUp()
        }
        XCTAssertTrue(cashflow.waitForExistence(timeout: 20))
        attachment(app, "profile-" + mode)
        app.tabBars.buttons["首页"].tap()
        XCTAssertTrue(app.tabBars.buttons["首页"].isSelected)
        attachment(app, "home-" + mode)
        if mode == "native" {
            app.tabBars.buttons["我的"].tap()
            let logout = app.buttons["dashboard.logout"]
            for _ in 0..<5 { if logout.exists && logout.isHittable { break }; app.swipeDown() }
            // 退出后用同一合成账号重新认证，确认账号持久化且隐私默认重新隐藏。
            logout.tap()
            XCTAssertTrue(app.textFields["auth.username"].waitForExistence(timeout: 15))
            app.textFields["auth.username"].tap(); app.textFields["auth.username"].typeText(String(username))
            app.secureTextFields["auth.password"].tap(); app.secureTextFields["auth.password"].typeText("Synthetic-Only-123")
            app.buttons["auth.submit"].tap()
            XCTAssertTrue(app.tabBars.buttons["财富"].waitForExistence(timeout: 30))
            app.tabBars.buttons["财富"].tap()
            let privacy = app.buttons["dashboard.privacy"]
            XCTAssertTrue(privacy.waitForExistence(timeout: 15)); XCTAssertEqual(privacy.label, "显示资产")
        }
    }
    /// 保留当前页面截图作为设备用例证据。
    private func attachment(_ app: XCUIApplication, _ name: String) {
        let item = XCTAttachment(screenshot: app.screenshot()); item.name = name; item.lifetime = .keepAlways; add(item)
    }
}
