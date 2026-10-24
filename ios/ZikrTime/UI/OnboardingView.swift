import SwiftUI

/// Первый запуск: язык → знакомство → город.
struct OnboardingView: View {
    @EnvironmentObject private var store: Store
    @State private var step: Int

    init(startStep: Int = 0) {
        _step = State(initialValue: startStep)
    }

    var body: some View {
        switch step {
        case 0: languageStep
        case 1: welcomeStep
        default: cityStep
        }
    }

    private var languageStep: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 14) {
                        Image("Logo")
                            .resizable()
                            .frame(width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        Text(L("app_name")).font(.title2.weight(.semibold))
                    }
                    .padding(.top, 24)
                    Text(L("onboarding_language_title")).font(.largeTitle.weight(.bold))
                    Card(padding: 8) { LanguageList() }
                }
                .padding(.horizontal, 20)
            }
            PrimaryButton(title: L("action_continue")) { step = 1 }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
        }
        .background(AuroraBackground())
    }

    private var welcomeStep: some View {
        ZStack {
            Brand.nightGradient.ignoresSafeArea()
            StarPattern(color: Brand.gold.opacity(0.09), cell: 72).ignoresSafeArea()
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 0) {
                        Image("Logo")
                            .resizable()
                            .frame(width: 120, height: 120)
                            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                            .shadow(color: .black.opacity(0.3), radius: 16, y: 8)
                            .padding(.top, 32)
                        Text(L("app_name"))
                            .font(.system(size: 36, weight: .semibold))
                            .foregroundStyle(Brand.cream)
                            .padding(.top, 20)
                        Text(L("slogan"))
                            .font(.headline)
                            .foregroundStyle(Brand.gold)
                            .multilineTextAlignment(.center)
                        VStack(alignment: .leading, spacing: 18) {
                            Feature(icon: "building.columns", text: L("onboarding_feature_times"))
                            Feature(icon: "alarm", text: L("onboarding_feature_alarm"))
                            Feature(icon: "book", text: L("onboarding_feature_quiz"))
                            Feature(icon: "location.north.circle", text: L("onboarding_feature_tools"))
                        }
                        .padding(.top, 32)
                    }
                    .padding(.horizontal, 28)
                }
                PrimaryButton(title: L("action_start"), color: Brand.gold, textColor: Brand.onGold) { step = 2 }
                    .padding(.horizontal, 28)
                    .padding(.vertical, 16)
            }
        }
    }

    private var cityStep: some View {
        Page {
            Text(L("onboarding_city_title")).font(.largeTitle.weight(.bold)).padding(.top, 16)
            Text(L("onboarding_city_text")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            CityPicker { city in
                store.city = city
                store.onboarded = true
                Task {
                    if !QA.active { await PrayerNotifications.requestPermission() }
                    await OnlineTimes.shared.refresh(force: true)
                    await Lifecycle.reschedule()
                }
            }
        }
    }
}

private struct Feature: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundStyle(Brand.gold)
                .frame(width: 42, height: 42)
                .background(Brand.cream.opacity(0.1), in: Circle())
            Text(text).font(.body).foregroundStyle(Brand.cream)
            Spacer(minLength: 0)
        }
    }
}
