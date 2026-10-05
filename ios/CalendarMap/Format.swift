import Foundation
import Shared

/** Date and time wording, mirroring the Android app's TimeFormat. */
enum Fmt {
    static let time: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .none
        f.timeStyle = .short
        return f
    }()

    static let shortDay: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("EEEdMMM")
        return f
    }()

    static let longDay: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .full
        f.timeStyle = .none
        return f
    }()

    private static var cal: Calendar { Calendar.current }

    /** Last calendar day an all-day event covers (EventKit ends them at 23:59:59 or next midnight). */
    static func lastDay(_ e: CalEvent) -> Date {
        cal.startOfDay(for: e.end.addingTimeInterval(-1))
    }

    /** Compact label for pins, e.g. "Tue 7 Oct · 14:00–15:30". */
    static func pinLabel(_ e: CalEvent) -> String {
        let prefix = cal.isDateInToday(e.begin) ? "" : shortDay.string(from: e.begin) + " · "
        return prefix + timeRange(e)
    }

    static func timeRange(_ e: CalEvent) -> String {
        if e.allDay {
            let last = lastDay(e)
            return cal.isDate(last, inSameDayAs: e.begin) ? "All day" : "All day → \(shortDay.string(from: last))"
        }
        if cal.isDate(e.begin, inSameDayAs: e.end) {
            return "\(time.string(from: e.begin))–\(time.string(from: e.end))"
        }
        return "\(time.string(from: e.begin)) → \(shortDay.string(from: e.end)) \(time.string(from: e.end))"
    }

    static func startFull(_ e: CalEvent) -> String {
        e.allDay ? longDay.string(from: e.begin) : "\(longDay.string(from: e.begin)), \(time.string(from: e.begin))"
    }

    static func endFull(_ e: CalEvent) -> String {
        e.allDay ? longDay.string(from: lastDay(e)) : "\(longDay.string(from: e.end)), \(time.string(from: e.end))"
    }

    /** Local clock time, with the day added when it isn't today. */
    static func clock(_ ms: Int64) -> String {
        let d = Date(ms: ms)
        return cal.isDateInToday(d) ? time.string(from: d) : "\(shortDay.string(from: d)) \(time.string(from: d))"
    }

    static func duration(_ ms: Int64) -> String { Durations.shared.format(ms: ms) }

    static func dayTitle(_ ms: Int64) -> String {
        let d = Date(ms: ms)
        if cal.isDateInToday(d) { return "Today" }
        if cal.isDateInTomorrow(d) { return "Tomorrow" }
        return longDay.string(from: d)
    }

    static func day(_ epochDay: Int64) -> String { shortDay.string(from: EpochDay.date(epochDay)) }
}
