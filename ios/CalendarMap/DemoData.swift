import Foundation
import SwiftUI

/**
 * Made-up events in San Francisco, used when the app is launched with `-demo`
 * (UI tests and store screenshots). Times are relative to now so a route can
 * always be planned.
 */
enum DemoData {
    static var enabled: Bool { ProcessInfo.processInfo.arguments.contains("-demo") }

    static let me = Coord(lat: 37.7880, lon: -122.4005)

    static let calendars = [
        CalendarInfo(id: "work", name: "Work", account: "demo@example.com", color: .blue),
        CalendarInfo(id: "tech", name: "Tech Week", account: "demo@example.com", color: .orange),
        CalendarInfo(id: "friends", name: "Friends", account: "demo@example.com", color: .green),
    ]

    private struct Spec {
        let title: String
        let calendar: Int
        let startHours: Double
        let lengthHours: Double
        let location: String
        let coord: Coord?
    }

    private static let specs = [
        Spec(title: "Founders Coffee", calendar: 0, startHours: 1, lengthHours: 1.5,
             location: "Ferry Building, 1 Ferry Building, San Francisco", coord: Coord(lat: 37.7955, lon: -122.3937)),
        Spec(title: "AI Demo Night", calendar: 1, startHours: 2, lengthHours: 3,
             location: "Moscone Center West, 800 Howard St, San Francisco", coord: Coord(lat: 37.7837, lon: -122.4011)),
        Spec(title: "Design Systems Meetup", calendar: 1, startHours: 2.5, lengthHours: 2,
             location: "Salesforce Park, 425 Mission St, San Francisco", coord: Coord(lat: 37.7897, lon: -122.3966)),
        Spec(title: "Open Source Hack Night", calendar: 1, startHours: 6.5, lengthHours: 3,
             location: "Dolores Park, San Francisco", coord: Coord(lat: 37.7596, lon: -122.4269)),
        Spec(title: "Team standup", calendar: 0, startHours: 0.5, lengthHours: 0.25,
             location: "Google Meet", coord: nil),
        Spec(title: "Product Lunch", calendar: 0, startHours: 24, lengthHours: 1.5,
             location: "Washington Square, San Francisco", coord: Coord(lat: 37.8008, lon: -122.4100)),
        Spec(title: "Rooftop Mixer", calendar: 2, startHours: 29, lengthHours: 3,
             location: "Union Square, San Francisco", coord: Coord(lat: 37.7880, lon: -122.4075)),
        Spec(title: "Board Games & Pizza", calendar: 2, startHours: 52, lengthHours: 3,
             location: "Hayes Valley, San Francisco", coord: Coord(lat: 37.7765, lon: -122.4241)),
    ]

    /** Events plus the coordinates the geocoder would have found. */
    static func events(now: Date = Date()) -> (events: [CalEvent], coords: [String: Coord]) {
        // Start on the next full hour so times look natural.
        let base = Calendar.current.nextDate(after: now, matching: DateComponents(minute: 0), matchingPolicy: .nextTime) ?? now
        var coords: [String: Coord] = [:]
        let events = specs.enumerated().map { i, s -> CalEvent in
            if let c = s.coord { coords[s.location] = c }
            let cal = calendars[s.calendar]
            let begin = base.addingTimeInterval(s.startHours * 3600)
            return CalEvent(
                eventId: "demo-\(i)", title: s.title, begin: begin,
                end: begin.addingTimeInterval(s.lengthHours * 3600), allDay: false,
                location: s.location, notes: "A made-up event for the demo.",
                calendarId: cal.id, calendarName: cal.name, color: cal.color
            )
        }
        return (events, coords)
    }
}
