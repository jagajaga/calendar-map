import SwiftUI

@main
struct CalendarMapApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .task { await model.start() }
        }
    }
}

struct RootView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        if !model.disclosureAccepted {
            DisclosureView()
        } else if model.permissionsRequested && !model.calendarAuthorized {
            PermissionView()
        } else {
            MainView()
        }
    }
}
