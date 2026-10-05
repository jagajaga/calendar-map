@testable import CalendarMap
import Shared
import XCTest

/** The Kotlin shared module as Swift sees it, plus the Swift-only glue around it. */
final class SharedInteropTests: XCTestCase {
    private let min: Int64 = 60_000
    private var hour: Int64 { 60 * min }

    func testPlannerFitsOverlappingEventsWithShortStays() {
        let stops = [PlanStop(begin: 0, end: 2 * hour), PlanStop(begin: hour, end: 3 * hour)]
        let m = TravelMatrix(size: 2)
        m.put(from: 0, to: 1, ms: 20 * min)
        m.put(from: 1, to: 0, ms: 20 * min)
        let plan = RoutePlanner.shared.plan(stops: stops, matrix: m, hasStart: false,
                                            minStayMs: KotlinLong(value: 30 * min), now: 0)
        XCTAssertEqual(plan.visits.map(\.stop), [0, 1])
        XCTAssertTrue(plan.missed.isEmpty)

        let whole = RoutePlanner.shared.plan(stops: stops, matrix: m, hasStart: false, minStayMs: nil, now: 0)
        XCTAssertEqual(whole.visits.count, 1)
        XCTAssertEqual(whole.missed.count, 1)
    }

    func testFreeTimeWordingThroughSwift() {
        let gap = PlanGap(beforeVisit: 1, from: 18 * hour, until: 20 * hour + 30 * min)
        let text = FreeTime.shared.describe(gap: gap, next: "Demo Night", atStart: false, zoneId: "UTC",
                                            clock: { "t\($0.int64Value / 60_000)" })
        XCTAssertEqual(text, "You have 2 h 30 min free (t1080–t1230) to do whatever you want before heading to Demo Night.")
    }

    func testGeoTextAndBounds() {
        XCTAssertTrue(GeoText.shared.looksVirtual(text: "https://zoom.us/j/1"))
        XCTAssertEqual(GeoText.shared.parseCoordinates(text: "52.52, 13.405"), LatLon(lat: 52.52, lon: 13.405))
        let b = Bounds.companion.fromCenter(lat: 37.77, lon: -122.42, latSpan: 0.1, lonSpan: 0.2)
        XCTAssertTrue(b.contains(p: LatLon(lat: 37.78, lon: -122.41)))
        XCTAssertFalse(b.contains(p: LatLon(lat: 37.80, lon: -122.27)))
    }

    func testOsrmParsingThroughSwift() {
        let pts = [LatLon(lat: 37.7749, lon: -122.4194), LatLon(lat: 37.7837, lon: -122.4089)]
        let body = #"{"code":"Ok","durations":[[0,600],[610,0]]}"#
        let m = Osrm.shared.parseTable(body: body, points: pts, mode: .walk)
        XCTAssertEqual(m?.at(from: 0, to: 1), 600_000)
        XCTAssertNil(Osrm.shared.parseTable(body: "nope", points: pts, mode: .walk))
    }

    func testTimeWindowCustomRangeIncludesEndDay() throws {
        let berlin = try XCTUnwrap(TimeZone(identifier: "Europe/Berlin"))
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = berlin
        let start = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 10, day: 10)))
        var s = AppSettings()
        s.preset = .custom
        s.customStartDay = EpochDay.of(start, calendar: cal)
        s.customEndDay = s.customStartDay! + 2
        let w = s.timeWindow(now: start, zone: berlin)
        XCTAssertEqual(w.start, start)
        XCTAssertEqual(w.end, cal.date(byAdding: .day, value: 3, to: start))
    }

    func testEpochDayRoundTrip() {
        let day: Int64 = 20_731 // 2026-10-05
        XCTAssertEqual(EpochDay.of(EpochDay.date(day)), day)
    }

    func testRadiusScaleRoundTripsDefault() {
        XCTAssertEqual(RadiusScale.km(RadiusScale.position(50)), 50)
        XCTAssertEqual(RadiusScale.km(0), 1)
        XCTAssertEqual(RadiusScale.km(1), 1_000)
    }

    func testSettingsPersistRoundTrip() throws {
        let d = try XCTUnwrap(UserDefaults(suiteName: "settings-test-\(UUID())"))
        var s = AppSettings()
        s.radiusKm = 120
        s.preset = .tomorrow
        s.areaMode = .radius
        s.travelMode = .car
        s.stayMinutes = nil
        s.selectedCalendarIds = ["a", "b"]
        s.save(d)
        XCTAssertEqual(AppSettings.load(d), s)
    }
}
