import Foundation
import Shared

struct AppSettings: Equatable {
    static let defaultRadiusKm = 50
    static let presets: [RangePreset] = [.today, .tomorrow, .week, .month, .custom]
    static let areaModes: [AreaMode] = [.visibleMap, .radius]
    static let travelModes: [TravelMode] = [.walk, .bike, .car]
    /** Minimum stay per event when planning a route; nil = the whole event. */
    static let stayOptions: [Int?] = [15, 30, 60, nil]

    var radiusKm = AppSettings.defaultRadiusKm
    /** nil means "all calendars", including ones added later. */
    var selectedCalendarIds: Set<String>?
    var preset: RangePreset = .week
    /** Inclusive local dates for the custom range, as epoch days. */
    var customStartDay: Int64?
    var customEndDay: Int64?
    /** Local times for the custom range, minutes after midnight; nil = whole days. */
    var customStartMinute: Int?
    var customEndMinute: Int?
    var showAllDay = true
    var areaMode: AreaMode = .visibleMap
    var travelMode: TravelMode = .walk
    var stayMinutes: Int? = 30
    var routeFromMyLocation = true

    /** The [start, end) window the current settings ask for (shared rules). */
    func timeWindow(now: Date = Date(), zone: TimeZone = .current) -> (start: Date, end: Date) {
        let w = TimeWindows.shared.compute(
            preset: preset,
            customStartDay: customStartDay.map { KotlinLong(value: $0) },
            customEndDay: customEndDay.map { KotlinLong(value: $0) },
            customStartMinute: customStartMinute.map { KotlinInt(value: Int32($0)) },
            customEndMinute: customEndMinute.map { KotlinInt(value: Int32($0)) },
            nowMs: now.ms,
            zoneId: zone.identifier
        )
        return (Date(ms: w.start), Date(ms: w.end))
    }

    // MARK: - Persistence

    private enum Key {
        static let radius = "radius_km", calendars = "calendar_ids", preset = "range_preset"
        static let start = "custom_start_day", end = "custom_end_day", allDay = "show_all_day"
        static let startMin = "custom_start_minute", endMin = "custom_end_minute"
        static let area = "area_mode", travel = "travel_mode", stay = "stay_minutes", fromMe = "route_from_my_location"
    }

    static func load(_ d: UserDefaults = .standard) -> AppSettings {
        var s = AppSettings()
        if d.object(forKey: Key.radius) != nil { s.radiusKm = d.integer(forKey: Key.radius) }
        if let ids = d.array(forKey: Key.calendars) as? [String] { s.selectedCalendarIds = Set(ids) }
        if let p = d.string(forKey: Key.preset), let v = presets.first(where: { $0.name == p }) { s.preset = v }
        if d.object(forKey: Key.start) != nil { s.customStartDay = Int64(d.integer(forKey: Key.start)) }
        if d.object(forKey: Key.end) != nil { s.customEndDay = Int64(d.integer(forKey: Key.end)) }
        if d.object(forKey: Key.startMin) != nil { s.customStartMinute = d.integer(forKey: Key.startMin) }
        if d.object(forKey: Key.endMin) != nil { s.customEndMinute = d.integer(forKey: Key.endMin) }
        if d.object(forKey: Key.allDay) != nil { s.showAllDay = d.bool(forKey: Key.allDay) }
        if let a = d.string(forKey: Key.area), let v = areaModes.first(where: { $0.name == a }) { s.areaMode = v }
        if let t = d.string(forKey: Key.travel), let v = travelModes.first(where: { $0.name == t }) { s.travelMode = v }
        if d.object(forKey: Key.stay) != nil {
            let m = d.integer(forKey: Key.stay)
            s.stayMinutes = m > 0 ? m : nil
        }
        if d.object(forKey: Key.fromMe) != nil { s.routeFromMyLocation = d.bool(forKey: Key.fromMe) }
        return s
    }

    func save(_ d: UserDefaults = .standard) {
        d.set(radiusKm, forKey: Key.radius)
        if let ids = selectedCalendarIds { d.set(Array(ids), forKey: Key.calendars) } else { d.removeObject(forKey: Key.calendars) }
        d.set(preset.name, forKey: Key.preset)
        if let v = customStartDay { d.set(Int(v), forKey: Key.start) } else { d.removeObject(forKey: Key.start) }
        if let v = customEndDay { d.set(Int(v), forKey: Key.end) } else { d.removeObject(forKey: Key.end) }
        if let v = customStartMinute { d.set(v, forKey: Key.startMin) } else { d.removeObject(forKey: Key.startMin) }
        if let v = customEndMinute { d.set(v, forKey: Key.endMin) } else { d.removeObject(forKey: Key.endMin) }
        d.set(showAllDay, forKey: Key.allDay)
        d.set(areaMode.name, forKey: Key.area)
        d.set(travelMode.name, forKey: Key.travel)
        d.set(stayMinutes ?? 0, forKey: Key.stay)
        d.set(routeFromMyLocation, forKey: Key.fromMe)
    }
}

/** Local calendar dates as epoch days (days since 1970-01-01), matching the shared module. */
enum EpochDay {
    static func of(_ date: Date, calendar: Calendar = .current) -> Int64 {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let midnight = utc.date(from: c)!
        return Int64((midnight.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    static func date(_ day: Int64, calendar: Calendar = .current) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let utcMidnight = Date(timeIntervalSince1970: Double(day) * 86_400)
        let c = utc.dateComponents([.year, .month, .day], from: utcMidnight)
        return calendar.date(from: c)!
    }
}
