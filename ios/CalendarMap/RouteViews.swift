import Shared
import SwiftUI

struct PlanningBar: View {
    @EnvironmentObject var model: AppModel
    let onBuild: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(model.selectedIds.isEmpty
                ? "Tap pins (or tick events in the list) to pick what you want to attend"
                : "\(model.selectedIds.count) of up to \(model.maxStops) events picked")
                .font(.callout)
            HStack {
                Button("Cancel", action: onCancel)
                Spacer()
                if model.routing {
                    ProgressView()
                    Text("Planning…")
                } else {
                    Button("Build route", action: onBuild)
                        .buttonStyle(.borderedProminent)
                        .disabled(model.selectedIds.isEmpty)
                        .accessibilityIdentifier("buildRouteButton")
                }
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .padding()
    }
}

struct RouteBar: View {
    let route: RouteResult
    let onShow: () -> Void
    let onClear: () -> Void

    var body: some View {
        HStack {
            Text(RouteText.summary(route)).font(.callout)
            Spacer()
            Button("Details", action: onShow).accessibilityIdentifier("routeDetailsButton")
            Button("Clear", action: onClear)
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .padding()
    }
}

enum RouteText {
    static func summary(_ route: RouteResult) -> String {
        let n = route.plan.visits.count
        let total = route.events.count
        let stops = n == total ? "\(n) event\(n == 1 ? "" : "s")" : "\(n) of \(total) events"
        let free = route.plan.freeBetweenMs
        let freeText = free >= FreeTime.shared.MIN_SHOWN_MS ? " · \(Fmt.duration(free)) free" : ""
        return "\(stops) · \(Fmt.duration(route.plan.totalTravelMs)) \(route.mode.label.lowercased())\(freeText)"
    }

    static func freeTime(_ gap: PlanGap, next: String, atStart: Bool) -> String? {
        FreeTime.shared.describe(
            gap: gap, next: next, atStart: atStart, zoneId: TimeZone.current.identifier,
            clock: { ms in Fmt.clock(ms.int64Value) }
        )
    }
}

struct RouteSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let route: RouteResult
    let onFocus: (Coord) -> Void

    var body: some View {
        let plan = route.plan
        NavigationStack {
            List {
                Section {
                    Text(RouteText.summary(route)).font(.subheadline)
                    if route.estimated {
                        Text("Routing service unreachable: times are straight-line estimates (orange line).")
                            .font(.caption).foregroundStyle(.red)
                    }
                    Picker("Travel", selection: $model.settings.travelMode) {
                        ForEach(AppSettings.travelModes, id: \.self) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Picker("Stay at each event at least", selection: $model.settings.stayMinutes) {
                        ForEach(AppSettings.stayOptions, id: \.self) { m in
                            Text(m.map { Fmt.duration(Int64($0) * 60_000) } ?? "Whole event").tag(m)
                        }
                    }
                    if model.myLocation != nil {
                        Toggle("Start from my location", isOn: $model.settings.routeFromMyLocation)
                    }
                    if model.routing { ProgressView().progressViewStyle(.linear) }
                }

                Section("Itinerary") {
                    if plan.visits.isEmpty {
                        Text("None of the picked events can be reached in time. Try a shorter stay or another way to travel.")
                    }
                    if let depart = plan.departAt?.int64Value, let first = plan.visits.first {
                        if let gap = plan.gaps.first(where: { $0.beforeVisit == 0 }),
                           let text = RouteText.freeTime(gap, next: route.event(first).event.title, atStart: true) {
                            FreeNote(text: text)
                        }
                        Text(depart <= Date().ms + 60_000 ? "Leave now from your location" : "Leave your location by \(Fmt.clock(depart))")
                            .font(.subheadline.bold())
                    }
                    ForEach(Array(plan.visits.enumerated()), id: \.offset) { k, v in
                        stop(k: k, v: v, plan: plan)
                    }
                }

                if !plan.missed.isEmpty {
                    Section("Can't fit in (\(plan.missed.count))") {
                        ForEach(plan.missed.map { Int(truncating: $0) }, id: \.self) { i in
                            let m = route.events[i]
                            Button("\(m.event.title) · \(Fmt.pinLabel(m.event))") { onFocus(m.point) }
                        }
                    }
                }

                Section {
                    if !plan.visits.isEmpty, let url = Links.googleRoute(route) {
                        Button("Open route in Google Maps") { openURL(url) }
                    }
                    Button("Change events") {
                        dismiss()
                        model.editSelection()
                    }
                    Button("Clear route", role: .destructive) {
                        dismiss()
                        model.cancelPlanning()
                    }
                }
            }
            .navigationTitle("Your route")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Hide") { dismiss() }.accessibilityIdentifier("hideRouteButton")
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func stop(k: Int, v: PlanVisit, plan: RoutePlan) -> some View {
        let m = route.event(v)
        let e = m.event
        if k > 0, let gap = plan.gaps.first(where: { $0.beforeVisit == Int32(k) }),
           let text = RouteText.freeTime(gap, next: e.title, atStart: false) {
            FreeNote(text: text)
        }
        let prevDay = k == 0 ? Date() : Date(ms: plan.visits[k - 1].stayFrom)
        if !Calendar.current.isDate(Date(ms: v.stayFrom), inSameDayAs: prevDay) {
            Text(Fmt.dayTitle(v.stayFrom)).font(.subheadline.bold()).foregroundStyle(Color.accentColor)
        }
        if k > 0 || route.start != nil {
            Text("↓  \(Fmt.duration(v.travelMs)) \(route.mode.label.lowercased())")
                .font(.caption).foregroundStyle(.secondary)
        }
        Button { onFocus(m.point) } label: {
            HStack(alignment: .top, spacing: 12) {
                Text("\(k + 1)").font(.headline).foregroundStyle(.white)
                    .frame(width: 28, height: 28).background(e.color, in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(e.title).font(.headline).foregroundStyle(.primary)
                    Text("Event: \(Fmt.pinLabel(e))").font(.caption).foregroundStyle(.secondary)
                    Text(arriveText(v, e)).font(.subheadline).foregroundStyle(.primary)
                    Text("Leave by \(Fmt.clock(v.leaveBy))").font(.subheadline.bold()).foregroundStyle(.primary)
                    if !e.location.isEmpty {
                        Text(e.location).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        if k == plan.visits.count - 1 {
            Text("Last event ends at \(Fmt.clock(e.end.ms)).").font(.caption).foregroundStyle(.secondary)
        }
    }

    private func arriveText(_ v: PlanVisit, _ e: CalEvent) -> String {
        let begin = e.begin.ms
        return v.arrive > begin
            ? "Arrive \(Fmt.clock(v.arrive)) (\(Fmt.duration(v.arrive - begin)) after it starts)"
            : "Arrive \(Fmt.clock(v.arrive))"
    }
}

struct FreeNote: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("☕")
            Text(text).font(.callout)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.15), in: RoundedRectangle(cornerRadius: 10))
    }
}
