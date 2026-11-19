import XCTest

final class FocusTasbihTests: XCTestCase {
    private func launch(_ screen: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-qa_screen", screen, "-qa_lang", "ru", "-qa_demo", "1"]
        app.launch()
        return app
    }

    func testFocusTimerPersistsAcrossLaunch() {
        continueAfterFailure = false
        let app = launch("focus")
        if app.buttons["focus_end"].exists { app.buttons["focus_end"].tap() }
        let start = app.buttons["focus_start"]
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        start.tap()
        XCTAssertTrue(app.staticTexts["focus_time_remaining"].waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        let stop = app.buttons["focus_end"]
        XCTAssertTrue(stop.waitForExistence(timeout: 10))
        stop.tap()
        XCTAssertTrue(start.waitForExistence(timeout: 5))
    }

    func testTasbihTapAndDownwardSwipeEachCountOnce() throws {
        continueAfterFailure = false
        let app = launch("tasbih")
        let settings = app.buttons["tasbih_settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        let beads = app.webViews.buttons.containing(NSPredicate(format: "label BEGINSWITH %@", "Коснитесь бусин")).firstMatch
        XCTAssertTrue(beads.waitForExistence(timeout: 12))

        func readCount() throws -> Int {
            settings.tap()
            let count = app.descendants(matching: .any).matching(identifier: "tasbih_count").firstMatch
            XCTAssertTrue(count.waitForExistence(timeout: 5))
            let n = try XCTUnwrap((count.value as? String).flatMap(Int.init))
            app.buttons["Готово"].tap()
            XCTAssertTrue(settings.waitForExistence(timeout: 5))
            return n
        }

        let before = try readCount()
        let point = app.coordinate(withNormalizedOffset: CGVector(dx: 0.19, dy: 0.46))
        point.tap()
        point.press(forDuration: 0.08, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.19, dy: 0.72)))
        XCTAssertEqual(try readCount(), before + 2)
    }
}
