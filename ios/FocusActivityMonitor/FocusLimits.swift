import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

enum FocusLimits {
    static let defaults = UserDefaults(suiteName: "group.com.zikrtime.ios")!
    static let activity = DeviceActivityName("ZikrTime.daily")
    static let event = DeviceActivityEvent.Name("ZikrTime.limit")
    static let durationKey = "focus_daily_minutes"
    private static let selectionKey = "focus_chosen_tokens"
    static var shields: ManagedSettingsStore { ManagedSettingsStore(named: .init("ZikrTimeDaily")) }
    static var isEnabled: Bool {
        get { defaults.bool(forKey: "focus_limit_enabled") }
        set { defaults.set(newValue, forKey: "focus_limit_enabled") }
    }
    static var selection: FamilyActivitySelection {
        guard let bytes = defaults.data(forKey: selectionKey),
              let value = try? PropertyListDecoder().decode(FamilyActivitySelection.self, from: bytes)
        else { return FamilyActivitySelection() }
        return value
    }
    static func save(_ selection: FamilyActivitySelection) {
        if let data = try? PropertyListEncoder().encode(selection) { defaults.set(data, forKey: selectionKey) }
    }
}
