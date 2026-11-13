package com.zikrtime.app.data

import org.junit.Assert.*
import org.junit.Test

class GuidePolicyTest {
    @Test fun aNewInstallationGetsTheGuide() {
        assertFalse(GuidePolicy.initialDismissed(emptyMap<String, Any>()))
    }

    @Test fun anExistingInstallationIsNotInterruptedByAnUpgrade() {
        assertTrue(GuidePolicy.initialDismissed(mapOf("onboarded" to true)))
    }

    @Test fun finishingCitySetupDoesNotDismissAnUnfinishedGuide() {
        assertFalse(GuidePolicy.initialDismissed(mapOf("onboarded" to true, GuidePolicy.DISMISSED to false)))
    }

    @Test fun skippingOrCompletingStaysDismissedAcrossRestarts() {
        assertTrue(GuidePolicy.initialDismissed(mapOf("onboarded" to true, GuidePolicy.DISMISSED to true)))
    }
}
