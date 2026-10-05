import MapKit
import Shared
import SwiftUI

struct MainView: View {
    @EnvironmentObject var model: AppModel
    @State private var camera: MapCameraPosition = .automatic
    @State private var selectedPlace: Place?
    @State private var showList = false
    @State private var showSettings = false
    @State private var showRange = false
    @State private var showRoute = false
    @State private var framed = false
    @State private var notice: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                RangeChips(onCustom: { showRange = true })
                StatusLine()
                ZStack(alignment: .bottom) {
                    map
                    controls
                }
            }
            .navigationTitle("Calendar Map")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showList = true } label: { Image(systemName: "list.bullet") }
                        .accessibilityLabel("Event list").accessibilityIdentifier("listButton")
                    Button { model.refresh() } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh")
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel("Settings").accessibilityIdentifier("settingsButton")
                }
            }
        }
        .sheet(item: $selectedPlace) { place in
            PlaceSheet(place: place, onToggle: toggle)
        }
        .sheet(isPresented: $showList) {
            EventListSheet(onPick: { m in
                showList = false
                focus(m.point)
                selectedPlace = model.places.first { $0.point == m.point }
                    ?? Place(point: m.point, events: [m.event], distanceKm: m.distanceKm)
            }, onToggle: toggle)
        }
        .sheet(isPresented: $showSettings) {
            SettingsSheet(onRadiusChanged: { frameMyArea() })
        }
        .sheet(isPresented: $showRange) {
            CustomRangeSheet()
        }
        .sheet(isPresented: $showRoute) {
            if let route = model.route {
                RouteSheet(route: route, onFocus: { c in
                    showRoute = false
                    focus(c)
                })
            }
        }
        .onChange(of: model.myLocation) { _, me in
            if me != nil && !framed {
                framed = true
                frameMyArea()
            }
        }
        .onChange(of: model.routeVersion) { _, _ in
            if let r = model.route {
                fit(r.path.isEmpty ? r.events.map(\.point) : r.path)
                showRoute = true
            } else {
                showRoute = false
            }
        }
        .alert(notice ?? "", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    // MARK: - Map

    private var map: some View {
        let order = model.route?.orderById ?? [:]
        let picked = (model.planning || model.routing) ? model.selectedIds : []
        return Map(position: $camera) {
            if let me = model.myLocation {
                if model.settings.areaMode == .radius {
                    MapCircle(center: me.cl, radius: CLLocationDistance(model.settings.radiusKm * 1000))
                        .foregroundStyle(Color.blue.opacity(0.08))
                        .stroke(Color.blue.opacity(0.6), lineWidth: 2)
                }
                Annotation("", coordinate: me.cl) {
                    Circle().fill(Color.blue).frame(width: 16, height: 16)
                        .overlay(Circle().stroke(Color.white, lineWidth: 3))
                        .shadow(radius: 2)
                }
            }
            if let r = model.route, r.path.count >= 2 {
                MapPolyline(coordinates: r.path.map(\.cl))
                    .stroke(r.estimated ? Color.orange : Color.blue, lineWidth: 5)
            }
            ForEach(model.places) { place in
                Annotation("", coordinate: place.point.cl, anchor: .bottom) {
                    PinView(place: place, selected: picked, order: order)
                        .onTapGesture { tap(place) }
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .mapControls {
            MapCompass()
            MapScaleView()
        }
        .onMapCameraChange(frequency: .onEnd) { context in
            model.setViewport(context.region)
        }
    }

    @ViewBuilder
    private var controls: some View {
        if model.planning || model.routing {
            PlanningBar(onBuild: model.buildRoute, onCancel: model.cancelPlanning)
        } else if let route = model.route, !showRoute {
            RouteBar(route: route, onShow: { showRoute = true }, onClear: model.cancelPlanning)
        } else {
            HStack {
                Spacer()
                VStack(alignment: .trailing, spacing: 12) {
                    if model.myLocation != nil {
                        Button(action: frameMyArea) {
                            Image(systemName: "location.fill").font(.title3).padding(14)
                        }
                        .background(.regularMaterial, in: Circle())
                        .accessibilityLabel("Show my area")
                    }
                    Button(action: model.startPlanning) {
                        Label("Plan route", systemImage: "map").font(.headline)
                            .padding(.horizontal, 18).padding(.vertical, 14)
                    }
                    .background(Color.accentColor, in: Capsule())
                    .foregroundStyle(.white)
                    .shadow(radius: 3)
                    .accessibilityIdentifier("planRouteButton")
                }
                .padding()
            }
        }
    }

    // MARK: - Actions

    private func tap(_ place: Place) {
        if model.planning && place.events.count == 1 {
            toggle(place.events[0].id)
        } else {
            selectedPlace = place
        }
    }

    private func toggle(_ id: String) {
        if !model.toggle(id) {
            notice = "A route can have at most \(model.maxStops) events."
        }
    }

    private func frameMyArea() {
        guard let me = model.myLocation else { return }
        let meters = model.settings.areaMode == .radius ? Double(model.settings.radiusKm) * 2_200 : 5_000
        withAnimation {
            camera = .region(MKCoordinateRegion(center: me.cl, latitudinalMeters: meters, longitudinalMeters: meters))
        }
    }

    private func focus(_ c: Coord) {
        withAnimation {
            camera = .region(MKCoordinateRegion(center: c.cl, latitudinalMeters: 1_500, longitudinalMeters: 1_500))
        }
    }

    private func fit(_ coords: [Coord]) {
        guard let first = coords.first else { return }
        if coords.count == 1 { return focus(first) }
        let lats = coords.map(\.lat)
        let lons = coords.map(\.lon)
        let center = CLLocationCoordinate2D(
            latitude: (lats.min()! + lats.max()!) / 2, longitude: (lons.min()! + lons.max()!) / 2
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max((lats.max()! - lats.min()!) * 1.5, 0.01),
            longitudeDelta: max((lons.max()! - lons.min()!) * 1.5, 0.01)
        )
        withAnimation { camera = .region(MKCoordinateRegion(center: center, span: span)) }
    }
}

// MARK: - Pins

struct PinView: View {
    let place: Place
    let selected: Set<String>
    let order: [String: Int]

    var body: some View {
        let evs = place.events
        let stops = evs.compactMap { order[$0.id] }.sorted()
        let picked = !stops.isEmpty || evs.contains { selected.contains($0.id) }
        let prefix = !stops.isEmpty ? stops.map(String.init).joined(separator: ",") + " · " : (picked ? "✓ " : "")
        let color = evs[0].color
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 1) {
                if evs.count == 1 {
                    Text(prefix + evs[0].title).font(.caption.bold()).lineLimit(1)
                    Text(Fmt.pinLabel(evs[0])).font(.caption2)
                } else {
                    Text(prefix + "\(evs.count) events").font(.caption.bold())
                    Text(Fmt.pinLabel(evs[0])).font(.caption2)
                    Text("…").font(.caption2)
                }
            }
            .frame(maxWidth: 190, alignment: .leading)
            .foregroundStyle(Color.white)
            .padding(.horizontal, 8).padding(.vertical, 5)
            .background(color, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(picked ? Color.black : Color.white, lineWidth: picked ? 3 : 1.5))
            Triangle().fill(color).frame(width: 14, height: 8)
        }
        .shadow(radius: 1.5)
        .accessibilityElement(children: .combine)
    }
}

struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

// MARK: - Filter bar

struct RangeChips: View {
    @EnvironmentObject var model: AppModel
    let onCustom: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(AppSettings.presets, id: \.self) { p in
                    Chip(label: label(p), selected: model.settings.preset == p) {
                        if p == .custom { onCustom() } else { model.settings.preset = p }
                    }
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
        }
    }

    private func label(_ p: RangePreset) -> String {
        let st = model.settings
        guard p == .custom, st.preset == .custom, let s = st.customStartDay else { return p.label }
        return Fmt.customRange(startDay: s, endDay: st.customEndDay ?? s,
                               startMinute: st.customStartMinute, endMinute: st.customEndMinute)
    }
}

struct Chip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if selected { Image(systemName: "checkmark").font(.caption.bold()) }
                Text(label).font(.subheadline)
            }
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(selected ? Color.accentColor.opacity(0.18) : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(selected ? Color.accentColor : Color.secondary.opacity(0.4)))
        }
        .buttonStyle(.plain)
    }
}

struct StatusLine: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if model.loading {
                ProgressView().progressViewStyle(.linear)
                Text(model.progress ?? "Loading…").font(.caption)
            } else {
                Text(summary).font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.bottom, 6)
    }

    private var summary: String {
        let n = model.shown.count
        let events = "\(n) event\(n == 1 ? "" : "s")"
        var parts: [String] = []
        if model.settings.areaMode == .visibleMap {
            parts.append("\(events) in view")
            if model.hiddenCount > 0 { parts.append("\(model.hiddenCount) off-screen") }
        } else if model.myLocation != nil {
            parts.append("\(events) within \(model.settings.radiusKm) km")
            if model.hiddenCount > 0 { parts.append("\(model.hiddenCount) farther away") }
        } else {
            parts.append("\(events) (location unknown, radius off)")
        }
        if !model.unmapped.isEmpty { parts.append("\(model.unmapped.count) without a place") }
        return parts.joined(separator: " · ")
    }
}
