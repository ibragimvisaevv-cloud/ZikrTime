package com.zikrtime.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceInfoTest {
    @Test fun linkDoesNotImplyReviewAndUnsafeLinksAreRejected() {
        assertNull(SourceInfo(webUrl="https://example.org/text").reviewedOriginal)
        assertNull(SourceInfo(webUrl="javascript:alert(1)").safeWebUrl)
        assertNull(SourceInfo(webUrl="https://user:password@example.org").safeWebUrl)
        assertEquals("https://example.org/text", SourceInfo(webUrl="https://example.org/text").safeWebUrl)
    }
    @Test fun changedOriginalDoesNotCarryReviewAndTranslationIsIndependent() {
        val evidence=ReviewEvidence("Test reviewer",1,"2026-10-27","Synthetic text")
        val source=SourceInfo(originalReview=evidence,translationReview=evidence,contentVersion=2,translationVersion=1)
        assertNull(source.reviewedOriginal)
        assertNotNull(source.reviewedTranslation)
        assertNull(source.copy(translationVersion=2).reviewedTranslation)
        assertNull(SourceInfo(originalReview=evidence.copy(scope="")).reviewedOriginal)
    }
    @Test fun malformedReviewEvidenceNeverBecomesVerified() {
        val raw = JSONObject("""{"originalReview":{"reviewer":123,"version":1,"scope":true},"translationReview":{"reviewer":"Test","version":0,"scope":"Test"}}""")
        val source = SourceInfo.fromJson(raw)
        assertNull(source.reviewedOriginal)
        assertNull(source.reviewedTranslation)
    }
    @Test fun legacyAndPartialSourcesRemainExplicitlyUnknown() {
        assertNull(SourceInfo.fromJson(null).reviewedOriginal)
        val source=SourceInfo.fromJson(JSONObject("""{"reference":"Synthetic reference","originalReview":{"reviewer":"Test reviewer","version":1,"scope":"Test original"},"translationReview":{"version":1}}"""))
        assertEquals("Synthetic reference",source.originalReference)
        assertNotNull(source.reviewedOriginal)
        assertNull(source.reviewedTranslation)
        assertNull(source.translationAttribution)
    }
}
