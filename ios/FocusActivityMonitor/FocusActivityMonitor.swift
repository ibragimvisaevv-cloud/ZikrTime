import DeviceActivity
import FamilyControls
import ManagedSettings

final class FocusActivityMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        if activity == FocusLimits.activity { FocusLimits.shields.clearAllSettings() }
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        guard activity == FocusLimits.activity, event == FocusLimits.event, FocusLimits.isEnabled else { return }
        let selection = FocusLimits.selection
        let shields = FocusLimits.shields
        shields.shield.applications = selection.applicationTokens
        shields.shield.webDomains = selection.webDomainTokens
        shields.shield.applicationCategories = .specific(selection.categoryTokens)
        shields.shield.webDomainCategories = .specific(selection.categoryTokens)
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        if activity == FocusLimits.activity { FocusLimits.shields.clearAllSettings() }
    }
}
