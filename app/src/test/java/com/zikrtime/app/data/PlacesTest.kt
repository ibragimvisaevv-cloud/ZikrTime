package com.zikrtime.app.data

import org.junit.Assert.*
import org.junit.Test

class PlacesTest {
    private fun place(fields: String = ""): Places.Place = Places.parse(
        """{"places":[{"id":"osm_node_123","name":"Test","lat":55.7,"lon":37.6$fields}]}"""
    ).places.single()

    @Test fun missingFieldsStayUnknownAndTechnicalDatesDoNotBecomeConfirmation() {
        val p = place(",\"created\":123,\"updated\":456,\"imported_at\":\"2026-01-01\"")
        assertNull(p.wudu)
        assertNull(p.women)
        assertEquals("", p.hours)
        assertEquals("", p.entrance)
        assertEquals("", p.accessConditions)
        assertEquals("", p.source)
        assertEquals("", p.confirmedAt)
    }

    @Test fun booleansAreStrictAndExplicitUnknownOverridesLegacyGender() {
        for (token in listOf("null", "\"false\"", "\"true\"", "0", "1", "{}", "[]")) {
            val p = place(",\"who\":\"both\",\"wudu\":$token,\"women\":$token")
            assertNull("$token is not Boolean false", p.wudu)
            assertNull("$token is not a legacy gender value", p.women)
        }
        assertEquals(true, place(",\"wudu\":true,\"women\":true").wudu)
        assertEquals(false, place(",\"wudu\":false,\"women\":false").women)
        assertEquals(true, place(",\"who\":\"both\"").women)
        assertEquals(false, place(",\"who\":\"men\"").women)
        assertNull(place(",\"who\":\"all\"").women)
    }

    @Test fun optionalFieldsAreTextAndConfirmationMustBeAnActualIsoDate() {
        val p = place(",\"src\":\"osm\",\"access_conditions\":\"Ask reception\",\"entrance\":\"East\",\"source_details\":\"Phone call\",\"confirmed_at\":\"2026-01-01\"")
        assertEquals("osm", p.source)
        assertEquals("Ask reception", p.accessConditions)
        assertEquals("East", p.entrance)
        assertEquals("Phone call", p.sourceDetails)
        assertEquals("2026-01-01", p.confirmedAt)
        for (bad in listOf("2026-02-30", "2026-1-01", "123")) assertEquals("", Places.confirmedDate(bad))
        val malformed = place(",\"access_conditions\":null,\"entrance\":123,\"source_details\":false,\"hours\":null")
        assertEquals("", malformed.accessConditions)
        assertEquals("", malformed.entrance)
        assertEquals("", malformed.sourceDetails)
        assertEquals("", malformed.hours)
    }

    @Test fun malformedEntriesAndDuplicateIdsCannotBreakTheList() {
        val d = Places.parse("""{"places":[null,{},42,{"id":"bad","name":"Invalid","lat":95,"lon":0},{"id":"ok","name":"Valid","lat":0,"lon":0},{"id":"ok","name":"Duplicate","lat":1,"lon":1}]}""")
        assertEquals(listOf("ok"), d.places.map { it.id })
        assertEquals("Valid", d.places.single().name)
    }

    @Test fun unsupportedOldBackendDoesNotOfferAnUnavailableSubmissionFlow() {
        assertFalse(Places.parse("""{"places":[]}""").correctionsEnabled)
        assertFalse(Places.parse("""{"places":[],"corrections_enabled":"true"}""").correctionsEnabled)
        assertTrue(Places.parse("""{"places":[],"corrections_enabled":true}""").correctionsEnabled)
    }

    @Test fun correctionLinkPreservesExactIdAndRejectsUnsupportedPayloads() {
        assertEquals("https://t.me/ZikrTimeTestBot?start=fix_osm_node_123", Places.correctionUrl("ZikrTimeTestBot", "osm_node_123"))
        for (id in listOf("", "../x", "id&start=other", "x".repeat(49))) assertNull(Places.correctionUrl("ZikrTimeTestBot", id))
        assertNull(Places.correctionUrl("", "osm_node_123"))
        assertNull(Places.correctionUrl("bad/bot", "osm_node_123"))
    }
}
