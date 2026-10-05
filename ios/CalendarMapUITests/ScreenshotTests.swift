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
        sleep(8) // let map tiles load and system banners go away
        shot(app, "01-map")

        openList(app)
        shot(app, "02-list")
        closeSheet(app)

        app.buttons["planRouteButton"].tap()
        XCTAssertTrue(app.buttons["buildRouteButton"].waitForExistence(timeout: 5))
        openList(app)
        for title in ["Founders Coffee", "AI Demo Night", "Design Systems Meetup", "Open Source Hack Night"] {
            let row = app.buttons["row-\(title)"]
            XCTAssertTrue(row.waitForExistence(timeout: 5), "missing row \(title)")
            row.tap()
        }
        shot(app, "03-picking")
        closeSheet(app)

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

    /** Sheets animate in and out; taps during the animation get dropped. */
    private func openList(_ app: XCUIApplication) {
        let done = app.buttons["doneButton"]
        for _ in 0..<3 where !done.exists {
            app.buttons["listButton"].tap()
            _ = done.waitForExistence(timeout: 4)
        }
        XCTAssertTrue(done.exists, "event list didn't open")
        sleep(1)
    }

    private func closeSheet(_ app: XCUIApplication) {
        let done = app.buttons["doneButton"]
        done.tap()
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: done)
        wait(for: [gone], timeout: 5)
        sleep(1)
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        add(a)
    }
}
