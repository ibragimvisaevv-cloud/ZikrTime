#if ZIKR_SCREEN_TIME
import DeviceActivity
import FamilyControls
import ManagedSettings
import SwiftUI

struct ScreenTimeControls: View {
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject private var authorization = AuthorizationCenter.shared
    @State private var selection = FocusLimits.selection
    @State private var minutes = (FocusLimits.defaults.object(forKey: FocusLimits.durationKey) as? Int) ?? 30
    @State private var enabled = FocusLimits.isEnabled
    @State private var choosing = false
    @State private var error: String?

    var body: some View {
        Card {
            Text(L("focus_daily_limit")).font(.title3.weight(.semibold))
            if authorization.authorizationStatus == .approved {
                VStack(alignment: .leading, spacing: 12) {
                    Button(L("focus_apps")) { choosing = true }.disabled(enabled)
                    Stepper(value: $minutes, in: 5...240, step: 5) {
                        Text(Duration.seconds(minutes * 60).formatted(.units(allowed: [.minutes], width: .wide)))
                    }.disabled(enabled)
                    Toggle(L("focus_enable"), isOn: Binding(get: { enabled }, set: setLimit))
                        .disabled(!enabled && selection.applicationTokens.isEmpty &&
                                  selection.categoryTokens.isEmpty && selection.webDomainTokens.isEmpty)
                }.padding(.top, 10)
            } else {
                Button(FocusText("authorize")) {
                    Task { @MainActor in
                        do { try await authorization.requestAuthorization(for: .individual) }
                        catch { self.error = error.localizedDescription }
                    }
                }.padding(.top, 10)
            }
            if let error { Text(error).font(.footnote).foregroundStyle(.red) }
        }
        .familyActivityPicker(isPresented: $choosing, selection: $selection)
        .onAppear(perform: reconcile)
        .onChange(of: scenePhase) { _, phase in if phase == .active { reconcile() } }
        .onChange(of: authorization.authorizationStatus) { _, _ in reconcile() }
        .onChange(of: choosing) { _, showing in
            if !showing && !enabled { FocusLimits.save(selection) }
        }
        .onChange(of: minutes) { _, value in FocusLimits.defaults.set(value, forKey: FocusLimits.durationKey) }
    }

    private func reconcile() {
        if authorization.authorizationStatus != .approved {
            selection = FamilyActivitySelection()
            FocusLimits.save(selection)
            if enabled { setLimit(false) }
        } else if enabled && !DeviceActivityCenter().activities.contains(FocusLimits.activity) {
            setLimit(false)
        }
    }

    private func setLimit(_ on: Bool) {
        error = nil
        let center = DeviceActivityCenter()
        if !on {
            FocusLimits.isEnabled = false
            center.stopMonitoring([FocusLimits.activity])
            FocusLimits.shields.clearAllSettings()
            enabled = false
            return
        }
        guard authorization.authorizationStatus == .approved else { return }
        do {
            FocusLimits.save(selection)
            FocusLimits.isEnabled = true
            let threshold = DateComponents(minute: minutes)
            let event = DeviceActivityEvent(applications: selection.applicationTokens, categories: selection.categoryTokens,
                                            webDomains: selection.webDomainTokens, threshold: threshold)
            let day = DeviceActivitySchedule(intervalStart: DateComponents(hour: 0, minute: 0),
                                             intervalEnd: DateComponents(hour: 23, minute: 59, second: 59), repeats: true)
            try center.startMonitoring(FocusLimits.activity, during: day, events: [FocusLimits.event: event])
            enabled = true
        } catch {
            setLimit(false)
            self.error = error.localizedDescription
        }
    }
}
#endif
