import MapKit
import Shared
import SwiftUI

@MainActor
final class AppModel: ObservableObject {
    @Published var settings: AppSettings {
        didSet {
            guard settings != oldValue else { return }
            if !demo { settings.save() }
            settingsChanged(from: oldValue)
        }
    }

    @Published var disclosureAccepted: Bool
    @Published var permissionsRequested = false
    @Published var calendarAuthorized = false
    @Published var locationAuthorized = false
    @Published var calendars: [CalendarInfo] = []
    @Published var myLocation: Coord?
    @Published var loading = false
    @Published var progress: String?
    /** Everything that geocoded, before the area filter. */
    @Published var mapped: [MappedEvent] = []
    @Published var unmapped: [CalEvent] = []
    @Published var viewport: Bounds?
    @Published var planning = false
    @Published var selectedIds: Set<String> = []
    @Published var routing = false
    @Published var route: RouteResult?
    /** Bumped whenever a new route arrives, so views can react to it. */
    @Published var routeVersion = 0

    let demo = DemoData.enabled
    private let calendarService = CalendarService()
    private let geo = GeoService()
    private let location = LocationService()
    private let router = RouterService()
    private var loadTask: Task<Void, Never>?
    private var routeTask: Task<Void, Never>?
    private static let disclosureKey = "disclosure_accepted_version"
    private static let disclosureVersion = 1

    init() {
        let demo = DemoData.enabled
        settings = demo ? AppSettings() : AppSettings.load()
        disclosureAccepted = demo || UserDefaults.standard.integer(forKey: Self.disclosureKey) >= Self.disclosureVersion
    }

    // MARK: - Startup and permissions

    func acceptDisclosure() {
        UserDefaults.standard.set(Self.disclosureVersion, forKey: Self.disclosureKey)
        disclosureAccepted = true
        Task { await start() }
    }

    func start() async {
        guard disclosureAccepted else { return }
        if demo {
            calendarAuthorized = true
            locationAuthorized = true
            myLocation = DemoData.me
            refresh(relocate: false)
            return
        }
        calendarAuthorized = await calendarService.requestAccess()
        await location.requestAuthorization()
        locationAuthorized = location.authorized
        permissionsRequested = true
        refresh(relocate: true)
    }

    // MARK: - Loading

    func refresh(relocate: Bool = true) {
        loadTask?.cancel()
        loadTask = Task { await load(relocate: relocate) }
    }

    func clearGeocodeCache() {
        geo.clearCache()
        refresh(relocate: false)
    }

    private func load(relocate: Bool) async {
        guard calendarAuthorized else { return }
        loading = true
        progress = "Reading calendars…"
        if !demo, relocate || myLocation == nil, let l = await location.current() {
            myLocation = Coord(lat: l.coordinate.latitude, lon: l.coordinate.longitude)
        }
        let s = settings
        let window = s.timeWindow()

        var events: [CalEvent]
        var known: [String: Coord] = [:]
        if demo {
            calendars = DemoData.calendars
            let d = DemoData.events()
            known = d.coords
            events = d.events.filter { e in
                e.end > window.start && e.begin < window.end &&
                    (s.selectedCalendarIds?.contains(e.calendarId) ?? true)
            }
        } else {
            calendars = calendarService.calendars()
            events = calendarService.events(start: window.start, end: window.end, calendarIds: s.selectedCalendarIds)
        }

        let visible = events.filter { s.showAllDay || !$0.allDay }
        var seen = Set<String>()
        let locations = visible.map(\.location).filter { !$0.isEmpty && seen.insert($0).inserted }
        var resolved: [String: Coord] = [:]
        for (i, loc) in locations.enumerated() {
            if Task.isCancelled { return }
            progress = "Finding places \(i + 1)/\(locations.count)…"
            resolved[loc] = demo ? known[loc] : await geo.resolve(loc)
        }
        if Task.isCancelled { return }

        let me = myLocation
        var m: [MappedEvent] = []
        var u: [CalEvent] = []
        for e in visible {
            if let p = resolved[e.location] {
                m.append(MappedEvent(event: e, point: p, distanceKm: me.map { Distance.shared.km(a: $0.latLon, b: p.latLon) }))
            } else {
                u.append(e)
            }
        }
        mapped = m
        unmapped = u
        let ids = Set(m.map(\.id))
        selectedIds = selectedIds.filter { ids.contains($0) }
        loading = false
        progress = nil
    }

    // MARK: - Filtering

    /** Events that pass the area filter: inside the radius, or inside the visible map. */
    var shown: [MappedEvent] {
        if settings.areaMode == .radius, myLocation != nil {
            return mapped.filter { ($0.distanceKm ?? 0) <= Double(settings.radiusKm) }
        }
        if settings.areaMode == .visibleMap, let v = viewport {
            return mapped.filter { v.contains(p: $0.point.latLon) }
        }
        return mapped
    }

    var hiddenCount: Int { mapped.count - shown.count }

    /** Pins to draw: only those near the visible area (lazy loading). */
    var places: [Place] {
        let near = viewport?.padded(fraction: 0.15)
        let candidates = shown.filter { near == nil || near!.contains(p: $0.point.latLon) }
        return Dictionary(grouping: candidates, by: \.point).map { point, list in
            Place(point: point, events: list.map(\.event).sorted { $0.begin < $1.begin }, distanceKm: list.first?.distanceKm)
        }
    }

    func setViewport(_ region: MKCoordinateRegion) {
        let b = Bounds.companion.fromCenter(
            lat: region.center.latitude, lon: region.center.longitude,
            latSpan: region.span.latitudeDelta, lonSpan: region.span.longitudeDelta
        )
        if viewport != b { viewport = b }
    }

    private func settingsChanged(from old: AppSettings) {
        var localOnly = settings
        localOnly.radiusKm = old.radiusKm
        localOnly.areaMode = old.areaMode
        localOnly.travelMode = old.travelMode
        localOnly.stayMinutes = old.stayMinutes
        localOnly.routeFromMyLocation = old.routeFromMyLocation
        if localOnly != old { refresh(relocate: false) }
        let routeInputsChanged = settings.travelMode != old.travelMode || settings.stayMinutes != old.stayMinutes ||
            settings.routeFromMyLocation != old.routeFromMyLocation
        if route != nil && routeInputsChanged { buildRoute() }
    }

    // MARK: - Route planning

    var maxStops: Int { Int(RoutePlanner.shared.MAX_STOPS) }

    func startPlanning() { planning = true }

    func cancelPlanning() {
        routeTask?.cancel()
        planning = false
        routing = false
        selectedIds = []
        route = nil
        routeVersion += 1
    }

    /** False when the selection is already full. */
    @discardableResult
    func toggle(_ id: String) -> Bool {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
            return true
        }
        guard selectedIds.count < maxStops else { return false }
        selectedIds.insert(id)
        return true
    }

    /** Back to picking events, keeping the selection. */
    func editSelection() {
        route = nil
        planning = true
        routeVersion += 1
    }

    func buildRoute() {
        let chosen = mapped.filter { selectedIds.contains($0.id) }.sorted { $0.event.begin < $1.event.begin }
        guard !chosen.isEmpty else { return }
        let s = settings
        let start = s.routeFromMyLocation ? myLocation : nil
        routeTask?.cancel()
        routing = true
        routeTask = Task {
            let points = chosen.map(\.point.latLon) + (start.map { [$0.latLon] } ?? [])
            let (matrix, est1) = await router.matrix(points: points, mode: s.travelMode)
            let plan = RoutePlanner.shared.plan(
                stops: chosen.map { PlanStop(begin: $0.event.begin.ms, end: $0.event.end.ms) },
                matrix: matrix,
                hasStart: start != nil,
                minStayMs: s.stayMinutes.map { KotlinLong(value: Int64($0) * 60_000) },
                now: Date().ms
            )
            let ordered = (start.map { [$0] } ?? []) + plan.visits.map { chosen[Int($0.stop)].point }
            let (path, est2) = await router.path(points: ordered.map(\.latLon), mode: s.travelMode)
            if Task.isCancelled { return }
            routing = false
            planning = false
            route = RouteResult(events: chosen, plan: plan, path: path.map { Coord($0) }, start: start,
                                mode: s.travelMode, estimated: est1 || est2)
            routeVersion += 1
        }
    }
}
