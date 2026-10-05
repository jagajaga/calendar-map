import CoreLocation
import Shared
import SwiftUI

struct Coord: Hashable {
    let lat: Double
    let lon: Double

    init(lat: Double, lon: Double) {
        self.lat = lat
        self.lon = lon
    }

    init(_ p: LatLon) {
        self.init(lat: p.lat, lon: p.lon)
    }

    var cl: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lon) }
    var latLon: LatLon { LatLon(lat: lat, lon: lon) }
}

struct CalendarInfo: Identifiable, Hashable {
    let id: String
    let name: String
    let account: String
    let color: Color
}

struct CalEvent: Identifiable, Hashable {
    let eventId: String
    let title: String
    let begin: Date
    let end: Date
    let allDay: Bool
    let location: String
    let notes: String
    let calendarId: String
    let calendarName: String
    let color: Color

    /** Unique per occurrence: recurring events share `eventId`. */
    var id: String { "\(eventId)@\(begin.ms)" }
}

struct MappedEvent: Identifiable, Hashable {
    let event: CalEvent
    let point: Coord
    let distanceKm: Double?
    var id: String { event.id }
}

/** Events that share one geocoded point; shown as a single pin. */
struct Place: Identifiable, Hashable {
    let point: Coord
    let events: [CalEvent]
    let distanceKm: Double?
    var id: String { "\(point.lat),\(point.lon)" }
}

struct RouteResult {
    /** Selected events, indexed by `PlanVisit.stop`. */
    let events: [MappedEvent]
    let plan: RoutePlan
    let path: [Coord]
    let start: Coord?
    let mode: TravelMode
    /** True when travel times are straight-line guesses (routing server unreachable). */
    let estimated: Bool

    /** Route position (1-based) of each attended event, by event id. */
    var orderById: [String: Int] {
        var out: [String: Int] = [:]
        for (i, v) in plan.visits.enumerated() {
            out[events[Int(v.stop)].event.id] = i + 1
        }
        return out
    }

    func event(_ v: PlanVisit) -> MappedEvent { events[Int(v.stop)] }
}

extension Date {
    init(ms: Int64) {
        self.init(timeIntervalSince1970: Double(ms) / 1000)
    }

    var ms: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
}
