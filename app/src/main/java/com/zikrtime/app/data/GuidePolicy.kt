package com.zikrtime.app.data

/** An unfinished first-run guide survives restarts; upgrades never interrupt existing users. */
object GuidePolicy {
    const val DISMISSED = "guide_dismissed_v1"

    fun initialDismissed(settings: Map<String, *>): Boolean =
        settings[DISMISSED] as? Boolean ?: (settings["onboarded"] == true)
}
