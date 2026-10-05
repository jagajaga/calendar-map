import XCTest

/**
 * Drives the app in demo mode (made-up San Francisco events) and saves
 * screenshots as test attachments. CI exports them as an artifact.
 */
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func testMainFlow() {
        let app = XCUIApplication()
        app.launchArguments = ["-demo"]
        app.launch()

        XCTAssertTrue(app.buttons["planRouteButton"].waitForExistence(timeout: 20))
        sleep(4) // let map tiles load
        shot(app, "01-map")

        app.buttons["listButton"].tap()
        XCTAssertTrue(app.buttons["doneButton"].waitForExistence(timeout: 5))
        shot(app, "02-list")
        app.buttons["doneButton"].tap()

        app.buttons["planRouteButton"].tap()
        app.buttons["listButton"].tap()
        for title in ["Founders Coffee", "AI Demo Night", "Design Systems Meetup", "Open Source Hack Night"] {
            let row = app.buttons["row-\(title)"]
            XCTAssertTrue(row.waitForExistence(timeout: 5), "missing row \(title)")
            row.tap()
        }
        shot(app, "03-picking")
        app.buttons["doneButton"].tap()

        let build = app.buttons["buildRouteButton"]
        XCTAssertTrue(build.waitForExistence(timeout: 5))
        build.tap()

        XCTAssertTrue(app.buttons["hideRouteButton"].waitForExistence(timeout: 45))
        sleep(1)
        shot(app, "04-route-itinerary")
        app.swipeUp()
        shot(app, "05-route-itinerary-more")
        app.buttons["hideRouteButton"].tap()
        sleep(3)
        shot(app, "06-route-map")

        app.buttons["settingsButton"].tap()
        sleep(1)
        shot(app, "07-settings")
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }
}
