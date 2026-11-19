import XCTest

final class QuranReaderTests: XCTestCase {
    func testReadQuranFromHome() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-qa_screen", "home", "-qa_lang", "ru", "-qa_demo", "1"]
        app.launch()

        func element(_ id: String) -> XCUIElement {
            app.descendants(matching: .any).matching(identifier: id).firstMatch
        }

        let read = element("home_read_quran")
        for _ in 0..<4 where !read.isHittable { app.swipeUp() }
        XCTAssertTrue(read.isHittable)
        read.tap()
        let surah = element("quran_surah_1")
        XCTAssertTrue(surah.waitForExistence(timeout: 15))
        surah.tap()

        let verse = element("quran_text_1")
        XCTAssertTrue(verse.waitForExistence(timeout: 10))
        verse.coordinate(withNormalizedOffset: CGVector(dx: 0.55, dy: 0.5)).tap()
        XCTAssertTrue(element("quran_inline_play_pause").exists)
        let selected = try XCTUnwrap(element("quran_location").label.split(separator: ":").last.flatMap { Int($0) })
        element("quran_mode_translation").tap()
        XCTAssertTrue(element("quran_translation_\(selected)").waitForExistence(timeout: 5))
        element("quran_inline_options").tap()
        XCTAssertTrue(element("quran_options_reciter").waitForExistence(timeout: 5))
        XCTAssertTrue(element("quran_options_speed").exists)
        XCTAssertTrue(element("quran_options_repeat").exists)
        let photo = XCTAttachment(screenshot: app.screenshot())
        photo.name = "Quran playback options"
        photo.lifetime = .keepAlways
        add(photo)
    }
}
