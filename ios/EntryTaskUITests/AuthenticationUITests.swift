import XCTest

/// 使用独立测试存储目录；不清理或覆盖用户的演示账号。
final class AuthenticationUITests: XCTestCase {
    private func app() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += ["--entrytask-ui-test"]
        app.launchEnvironment["ENTRYTASK_TEST_ID"] = UUID().uuidString
        app.launch()
        XCTAssertTrue(app.textFields["auth.username"].waitForExistence(timeout: 15))
        return app
    }
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
    func testRegistrationReturnsToSelectedDashboardAndFourTabs() throws {
        let app = app()
        app.buttons["auth.switch"].tap()
        let username = "ui-" + UUID().uuidString.prefix(8)
        app.textFields["auth.username"].tap(); app.textFields["auth.username"].typeText(String(username))
        app.secureTextFields["auth.password"].tap(); app.secureTextFields["auth.password"].typeText("Synthetic-Only-123")
        app.buttons["auth.submit"].tap()
        XCTAssertTrue(app.tabBars.buttons["财富"].waitForExistence(timeout: 30))
        XCTAssertFalse(app.buttons["auth.submit"].exists)
        let mode = try XCTUnwrap(Bundle(for: type(of: self)).object(forInfoDictionaryKey: "EntryTaskPageMode") as? String)
        XCTAssertTrue(app.otherElements[mode == "rn" ? "dashboard.rn" : "dashboard.native"].waitForExistence(timeout: 30))
        let search = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.otherElements["搜索"].exists || app.buttons["搜索"].exists || app.staticTexts["搜索"].exists
        }, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [search], timeout: 45), .completed)
        app.tabBars.buttons["财富"].tap()
        XCTAssertTrue(app.tabBars.buttons["财富"].isSelected)
        XCTAssertTrue(app.staticTexts["总资产（元）"].waitForExistence(timeout: 20))
        if mode == "native" {
            let privacy = app.buttons["dashboard.privacy"]
            XCTAssertTrue(privacy.waitForExistence(timeout: 15)); privacy.tap()
            XCTAssertEqual(privacy.label, "隐藏资产")
        }
        attachment(app, "wealth-" + mode)
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
    private func attachment(_ app: XCUIApplication, _ name: String) {
        let item = XCTAttachment(screenshot: app.screenshot()); item.name = name; item.lifetime = .keepAlways; add(item)
    }
}
