import CoreLocation
import EventKit
import Foundation
import Shared
import SwiftUI

/** Reads calendars and event occurrences with EventKit. */
final class CalendarService {
    let store = EKEventStore()

    var authorized: Bool { EKEventStore.authorizationStatus(for: .event) == .fullAccess }

    func requestAccess() async -> Bool {
        if authorized { return true }
        do {
            return try await store.requestFullAccessToEvents()
        } catch {
            return false
        }
    }

    func calendars() -> [CalendarInfo] {
        store.calendars(for: .event)
            .map { CalendarInfo(id: $0.calendarIdentifier, name: $0.title, account: $0.source?.title ?? "", color: Color(cgColor: $0.cgColor)) }
            .sorted { ($0.account, $0.name) < ($1.account, $1.name) }
    }

    /** Occurrences overlapping [start, end), with recurrences expanded; declined and cancelled ones dropped. */
    func events(start: Date, end: Date, calendarIds: Set<String>?) -> [CalEvent] {
        var cals: [EKCalendar]?
        if let ids = calendarIds {
            let picked = store.calendars(for: .event).filter { ids.contains($0.calendarIdentifier) }
            if picked.isEmpty { return [] }
            cals = picked
        }
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: cals)
        return store.events(matching: predicate)
            .filter { ev in
                if ev.status == .canceled { return false }
                let declined = ev.attendees?.contains { $0.isCurrentUser && $0.participantStatus == .declined } ?? false
                return !declined
            }
            .map { ev in
                CalEvent(
                    eventId: ev.eventIdentifier ?? UUID().uuidString,
                    title: (ev.title?.isEmpty == false) ? ev.title! : "(no title)",
                    begin: ev.startDate,
                    end: ev.endDate,
                    allDay: ev.isAllDay,
                    location: (ev.location ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
                    notes: ev.notes ?? "",
                    calendarId: ev.calendar.calendarIdentifier,
                    calendarName: ev.calendar.title,
                    color: Color(cgColor: ev.calendar.cgColor)
                )
            }
            .sorted { $0.begin < $1.begin }
    }
}

/**
 * Turns free-text event locations into coordinates with Apple's geocoder,
 * caching hits forever and misses for a day.
 */
final class GeoService {
    private let geocoder = CLGeocoder()
    private let defaults = UserDefaults.standard
    private let key = "geocache"
    private static let missTTL: TimeInterval = 24 * 60 * 60

    func resolve(_ location: String) async -> Coord? {
        let text = location.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty || GeoText.shared.looksVirtual(text: text) { return nil }
        if let p = GeoText.shared.parseCoordinates(text: text) { return Coord(p) }

        let cacheKey = text.lowercased()
        var cache = defaults.dictionary(forKey: key) as? [String: String] ?? [:]
        if let cached = cache[cacheKey] {
            if cached.hasPrefix("miss:") {
                let ts = TimeInterval(cached.dropFirst(5)) ?? 0
                if Date().timeIntervalSince1970 - ts < Self.missTTL { return nil }
            } else if let p = GeoText.shared.parseCoordinates(text: cached) {
                return Coord(p)
            }
        }

        let result: Coord?
        do {
            let marks = try await geocoder.geocodeAddressString(text)
            result = marks.first?.location.map { Coord(lat: $0.coordinate.latitude, lon: $0.coordinate.longitude) }
        } catch let error as CLError where error.code == .network {
            return nil // offline or throttled: don't cache, try again next refresh
        } catch {
            result = nil
        }
        cache[cacheKey] = result.map { "\($0.lat),\($0.lon)" } ?? "miss:\(Date().timeIntervalSince1970)"
        defaults.set(cache, forKey: key)
        return result
    }

    func clearCache() {
        defaults.removeObject(forKey: key)
    }
}

/** One-shot location fixes with async/await. */
@MainActor
final class LocationService: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var authWaiters: [CheckedContinuation<Void, Never>] = []
    private var fixWaiters: [CheckedContinuation<CLLocation?, Never>] = []

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var authorized: Bool {
        manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways
    }

    func requestAuthorization() async {
        guard manager.authorizationStatus == .notDetermined else { return }
        await withCheckedContinuation { c in
            authWaiters.append(c)
            manager.requestWhenInUseAuthorization()
        }
    }

    func current() async -> CLLocation? {
        guard authorized else { return nil }
        if let l = manager.location, -l.timestamp.timeIntervalSinceNow < 120 { return l }
        return await withCheckedContinuation { c in
            fixWaiters.append(c)
            if fixWaiters.count == 1 { manager.requestLocation() }
        }
    }

    private func finishAuth() {
        let w = authWaiters
        authWaiters = []
        w.forEach { $0.resume() }
    }

    private func finishFix(_ l: CLLocation?) {
        let w = fixWaiters
        fixWaiters = []
        w.forEach { $0.resume(returning: l) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            if status != .notDetermined { self.finishAuth() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in self.finishFix(l) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let l = manager.location
        Task { @MainActor in self.finishFix(l) }
    }
}

/** HTTP for OSRM; URLs, parsing and fallbacks come from the shared module. */
struct RouterService {
    static let userAgent = "me.jagajaga.calendarmap (github.com/jagajaga/calendar-map)"

    func matrix(points: [LatLon], mode: TravelMode) async -> (TravelMatrix, estimated: Bool) {
        if let body = await get(Osrm.shared.tableUrl(points: points, mode: mode)),
           let m = Osrm.shared.parseTable(body: body, points: points, mode: mode) {
            return (m, false)
        }
        return (Osrm.shared.estimatedMatrix(points: points, mode: mode), true)
    }

    func path(points: [LatLon], mode: TravelMode) async -> ([LatLon], estimated: Bool) {
        if points.count < 2 { return (points, false) }
        if let body = await get(Osrm.shared.routeUrl(points: points, mode: mode)),
           let path = Osrm.shared.parseRoute(body: body) {
            return (path, false)
        }
        return (points, true)
    }

    private func get(_ url: String) async -> String? {
        guard let u = URL(string: url) else { return nil }
        var req = URLRequest(url: u, timeoutInterval: 15)
        req.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return String(data: data, encoding: .utf8)
        } catch {
            return nil
        }
    }
}
