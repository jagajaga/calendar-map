import Shared
import SwiftUI

enum Links {
    static let privacyPolicy = URL(string: "https://jagajaga.me/calendarmap/")!
    static let sourceCode = URL(string: "https://github.com/jagajaga/calendar-map")!

    /** Google Maps app if installed, otherwise the Google Maps website. */
    static func googleMaps(query: String) -> URL {
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        if let app = URL(string: "comgooglemaps://?q=\(q)"), UIApplication.shared.canOpenURL(app) { return app }
        return URL(string: "https://www.google.com/maps/search/?api=1&query=\(q)")!
    }

    static func appleMaps(query: String, at c: Coord) -> URL {
        let q = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        return URL(string: "https://maps.apple.com/?q=\(q)&ll=\(c.lat),\(c.lon)")!
    }

    /** Opens the Calendar app on the event's day. */
    static func calendar(at date: Date) -> URL {
        URL(string: "calshow:\(Int(date.timeIntervalSinceReferenceDate))")!
    }

    /** The whole route in Google Maps; origin omitted = "from my location". */
    static func googleRoute(_ route: RouteResult) -> URL? {
        let stops = route.plan.visits.map(route.event)
        guard let firstStop = stops.first else { return nil }
        func place(_ m: MappedEvent) -> String { m.event.location.isEmpty ? "\(m.point.lat),\(m.point.lon)" : m.event.location }
        var c = URLComponents(string: "https://www.google.com/maps/dir/")!
        var items = [URLQueryItem(name: "api", value: "1"), URLQueryItem(name: "travelmode", value: route.mode.googleMode)]
        var rest = stops
        if route.start == nil {
            items.append(URLQueryItem(name: "origin", value: place(firstStop)))
            rest = Array(stops.dropFirst())
        }
        if rest.isEmpty {
            items.append(URLQueryItem(name: "destination", value: place(firstStop)))
        } else {
            items.append(URLQueryItem(name: "destination", value: place(rest.last!)))
            let via = rest.dropLast()
            if !via.isEmpty { items.append(URLQueryItem(name: "waypoints", value: via.map(place).joined(separator: "|"))) }
        }
        c.queryItems = items
        return c.url
    }
}

struct ColorDot: View {
    let color: Color
    var body: some View { Circle().fill(color).frame(width: 12, height: 12) }
}

// MARK: - Event details

struct PlaceSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let place: Place
    let onToggle: (String) -> Void

    var body: some View {
        NavigationStack {
            List {
                if model.planning {
                    Section("Pick events for your route") {
                        ForEach(place.events) { e in
                            Button { onToggle(e.id) } label: {
                                HStack {
                                    Image(systemName: model.selectedIds.contains(e.id) ? "checkmark.circle.fill" : "circle")
                                    VStack(alignment: .leading) {
                                        Text(e.title)
                                        Text(Fmt.pinLabel(e)).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ForEach(place.events) { e in
                        EventCard(event: e, point: place.point, distanceKm: place.distanceKm)
                    }
                }
            }
            .navigationTitle(place.events.count > 1 ? "\(place.events.count) events here" : "Event")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct EventCard: View {
    @Environment(\.openURL) private var openURL
    let event: CalEvent
    let point: Coord?
    let distanceKm: Double?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(event.title).font(.title3.bold())
            HStack { ColorDot(color: event.color); Text(event.calendarName).font(.subheadline) }
            row("Starts", Fmt.startFull(event))
            row("Ends", Fmt.endFull(event))
            if !event.location.isEmpty {
                row("Where", event.location + (distanceKm.map { String(format: " (%.1f km away)", $0) } ?? ""))
            }
            if !event.notes.isEmpty {
                Text(event.notes).font(.callout).lineLimit(8)
            }
            HStack {
                if !event.location.isEmpty || point != nil {
                    Button("Google Maps") {
                        openURL(Links.googleMaps(query: event.location.isEmpty ? "\(point!.lat),\(point!.lon)" : event.location))
                    }
                    .buttonStyle(.borderedProminent)
                    if let p = point {
                        Button("Apple Maps") { openURL(Links.appleMaps(query: event.location.isEmpty ? event.title : event.location, at: p)) }
                            .buttonStyle(.bordered)
                    }
                }
                Button("Calendar") { openURL(Links.calendar(at: event.begin)) }
                    .buttonStyle(.bordered)
            }
            .padding(.top, 4)
        }
        .padding(.vertical, 6)
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label).font(.subheadline.bold()).frame(width: 56, alignment: .leading)
            Text(value).font(.subheadline)
        }
    }
}

// MARK: - Event list

struct EventListSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let onPick: (MappedEvent) -> Void
    let onToggle: (String) -> Void

    var body: some View {
        let shown = model.shown.sorted { $0.event.begin < $1.event.begin }
        let shownIds = Set(shown.map(\.id))
        let hidden = model.mapped.filter { !shownIds.contains($0.id) }.sorted { $0.event.begin < $1.event.begin }
        let visibleMode = model.settings.areaMode == .visibleMap
        NavigationStack {
            List {
                if model.planning {
                    Text("Tick events for your route (\(model.selectedIds.count)/\(model.maxStops))")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Section("\(visibleMode ? "In view" : "On the map") (\(shown.count))") {
                    if shown.isEmpty { Text("No events with a place here.").foregroundStyle(.secondary) }
                    ForEach(shown) { m in row(m, dim: false) }
                }
                if !hidden.isEmpty {
                    Section("\(visibleMode ? "Off-screen" : "Farther than \(model.settings.radiusKm) km") (\(hidden.count))") {
                        ForEach(hidden) { m in row(m, dim: !model.planning) }
                    }
                }
                if !model.unmapped.isEmpty {
                    Section("No place on the map (\(model.unmapped.count))") {
                        ForEach(model.unmapped) { e in
                            Button { openURL(Links.calendar(at: e.begin)) } label: { EventRowLabel(event: e, distanceKm: nil, checked: nil) }
                                .opacity(0.6)
                        }
                    }
                }
            }
            .navigationTitle("Events")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.accessibilityIdentifier("doneButton")
                }
            }
        }
    }

    private func row(_ m: MappedEvent, dim: Bool) -> some View {
        Button {
            if model.planning { onToggle(m.id) } else { onPick(m) }
        } label: {
            EventRowLabel(event: m.event, distanceKm: m.distanceKm, checked: model.planning ? model.selectedIds.contains(m.id) : nil)
        }
        .opacity(dim ? 0.6 : 1)
        .accessibilityIdentifier("row-\(m.event.title)")
    }
}

struct EventRowLabel: View {
    let event: CalEvent
    let distanceKm: Double?
    let checked: Bool?

    var body: some View {
        HStack(spacing: 12) {
            if let c = checked {
                Image(systemName: c ? "checkmark.circle.fill" : "circle").foregroundStyle(Color.accentColor)
            }
            ColorDot(color: event.color)
            VStack(alignment: .leading, spacing: 2) {
                Text(event.title).foregroundStyle(.primary).lineLimit(1)
                Text(Fmt.pinLabel(event)).font(.caption).foregroundStyle(.secondary)
                if !event.location.isEmpty {
                    Text(event.location + (distanceKm.map { String(format: " · %.0f km", $0) } ?? ""))
                        .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
            }
        }
    }
}

// MARK: - Settings

struct SettingsSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let onRadiusChanged: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Show events") {
                    Picker("Area", selection: $model.settings.areaMode) {
                        ForEach(AppSettings.areaModes, id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    if model.settings.areaMode == .radius {
                        VStack(alignment: .leading) {
                            Text("Radius: \(model.settings.radiusKm) km")
                            Slider(value: Binding(
                                get: { RadiusScale.position(model.settings.radiusKm) },
                                set: { model.settings.radiusKm = RadiusScale.km($0) }
                            ), onEditingChanged: { editing in if !editing { onRadiusChanged() } })
                        }
                        if !model.locationAuthorized {
                            Text("Location access is off, so the radius can't be applied. Turn it on in Settings.")
                                .font(.caption).foregroundStyle(.red)
                        }
                    }
                    Toggle("Show all-day events", isOn: $model.settings.showAllDay)
                }
                Section {
                    ForEach(accounts, id: \.self) { account in
                        Text(account.isEmpty ? "Other" : account).font(.caption).foregroundStyle(.secondary)
                        ForEach(model.calendars.filter { $0.account == account }) { cal in
                            Toggle(isOn: Binding(get: { isOn(cal) }, set: { _ in toggle(cal) })) {
                                HStack { ColorDot(color: cal.color); Text(cal.name) }
                            }
                        }
                    }
                } header: {
                    HStack {
                        Text("Calendars")
                        Spacer()
                        Button("All") { model.settings.selectedCalendarIds = nil }
                        Button("None") { model.settings.selectedCalendarIds = [] }
                    }
                }
                Section {
                    Button("Re-look-up all event places") { model.clearGeocodeCache() }
                }
                Section("About & privacy") {
                    Text("Calendar Map \(version) · open source (MIT)").font(.footnote)
                    Link("Privacy policy", destination: Links.privacyPolicy)
                    Link("Source code", destination: Links.sourceCode)
                    Text("Maps by Apple. Routing by OSRM on routing.openstreetmap.de (FOSSGIS), map data © OpenStreetMap contributors.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }

    private var accounts: [String] {
        var seen = Set<String>()
        return model.calendars.map(\.account).filter { seen.insert($0).inserted }
    }

    private var version: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }

    private func isOn(_ cal: CalendarInfo) -> Bool {
        model.settings.selectedCalendarIds?.contains(cal.id) ?? true
    }

    private func toggle(_ cal: CalendarInfo) {
        let all = Set(model.calendars.map(\.id))
        var current = model.settings.selectedCalendarIds ?? all
        if current.contains(cal.id) { current.remove(cal.id) } else { current.insert(cal.id) }
        model.settings.selectedCalendarIds = current == all ? nil : current
    }
}

/** Logarithmic radius slider, so both 2 km and 500 km are easy to hit. */
enum RadiusScale {
    static let maxKm = 1_000.0

    static func position(_ km: Int) -> Double {
        log(Double(min(max(km, 1), Int(maxKm)))) / log(maxKm)
    }

    static func km(_ pos: Double) -> Int {
        let raw = pow(maxKm, pos)
        let step: Double = raw < 10 ? 1 : (raw < 100 ? 5 : 25)
        return min(max(Int((raw / step).rounded() * step), 1), Int(maxKm))
    }
}

struct CustomRangeSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var start = Calendar.current.startOfDay(for: Date())
    @State private var end = Calendar.current.startOfDay(for: Date()).addingTimeInterval(86_340)
    @State private var withTimes = false

    var body: some View {
        NavigationStack {
            Form {
                Toggle("Choose times", isOn: $withTimes)
                DatePicker("From", selection: $start, displayedComponents: withTimes ? [.date, .hourAndMinute] : [.date])
                DatePicker("Until", selection: $end, in: start..., displayedComponents: withTimes ? [.date, .hourAndMinute] : [.date])
                if withTimes && end <= start {
                    Text("The end must be after the start.").font(.caption).foregroundStyle(.red)
                }
            }
            .navigationTitle("Show events between")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") { apply() }.disabled(withTimes && end <= start)
                }
            }
            .onAppear(perform: load)
        }
        .presentationDetents([.medium, .large])
    }

    private func minuteOfDay(_ d: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func load() {
        let st = model.settings
        guard let s = st.customStartDay else { return }
        withTimes = st.customStartMinute != nil || st.customEndMinute != nil
        start = EpochDay.date(s).addingTimeInterval(TimeInterval((st.customStartMinute ?? 0) * 60))
        end = EpochDay.date(st.customEndDay ?? s).addingTimeInterval(TimeInterval((st.customEndMinute ?? 1439) * 60))
    }

    private func apply() {
        var s = model.settings
        s.preset = .custom
        s.customStartDay = EpochDay.of(start)
        s.customEndDay = EpochDay.of(max(start, end))
        s.customStartMinute = withTimes ? minuteOfDay(start) : nil
        s.customEndMinute = withTimes ? minuteOfDay(end) : nil
        model.settings = s
        dismiss()
    }
}

// MARK: - First run

struct DisclosureView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Before you start").font(.largeTitle.bold())
                Text("Calendar Map shows your calendar events on a map. Here is what it uses and where data goes.")
                point("Your calendar", "Read on this iPhone to find each event's time and place. Event titles, notes and guests never leave your device.")
                point("Your location", "Used on this iPhone to measure how far events are and as the starting point of a route. When a route starts from your location, its coordinates are sent to the routing service below.")
                point("Sent over the internet", "• Event location text (an address or place name) goes to Apple's geocoding service to find it on the map.\n• Maps are loaded from Apple Maps.\n• Coordinates of the events in a route (and your start point) go to the OpenStreetMap routing service run by FOSSGIS (routing.openstreetmap.de).")
                point("Never", "No accounts, ads, analytics or tracking. The developer receives no data from the app.")
                Button { model.acceptDisclosure() } label: {
                    Text("Continue").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
                Button("Read the privacy policy") { openURL(Links.privacyPolicy) }
                    .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
    }

    private func point(_ title: String, _ body: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline)
            Text(body).font(.callout)
        }
    }
}

struct PermissionView: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        ContentUnavailableView {
            Label("Calendar access needed", systemImage: "calendar.badge.exclamationmark")
        } description: {
            Text("Calendar Map needs full access to your calendars to place events on the map. Turn it on in Settings → Privacy & Security → Calendars.")
        } actions: {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
