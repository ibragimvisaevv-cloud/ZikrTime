package com.zikrtime.app.data

import org.json.JSONObject
import java.util.UUID

/** Separate version: old generic tasbih positions must not point into the new collection. */
object AzkarStore {
    private const val KEY = "azkar_reading_v2"
    private fun read() = runCatching { JSONObject(Store.getRaw(KEY) ?: "{}") }.getOrElse { JSONObject() }
    private fun save(value: JSONObject) = Store.putRaw(KEY, value.toString())
    val pendingPeriod: String? get() = read().let { root ->
        listOf(root.optString("last"), "morning", "evening").distinct().firstOrNull { p ->
            p in setOf("morning", "evening") && root.optJSONObject(p)?.optBoolean("complete", true) == false
        }
    }
    val hasPending: Boolean get() = pendingPeriod != null
    val pendingIndex: Int get() = index(pendingPeriod ?: "morning")
    fun index(period: String): Int = read().optJSONObject(period)?.optInt("index", 0)
        ?.coerceIn(AzkarContent.items(period).indices) ?: 0
    fun count(period: String, item: AzkarEntry): Int = read().optJSONObject(period)?.optJSONObject("counts")
        ?.optInt(item.id, 0)?.coerceIn(0, item.repetitions) ?: 0
    fun start(period: String) {
        require(period == "morning" || period == "evening")
        val root = read(); root.put("last", period)
        if (root.optJSONObject(period)?.optBoolean("complete", true) != false)
            root.put(period, JSONObject().put("index", 0).put("counts", JSONObject())
                .put("complete", false).put("sessionId", UUID.randomUUID().toString()))
        save(root)
    }
    fun move(period: String, index: Int) {
        val root = read(); val data = root.optJSONObject(period) ?: return
        data.put("index", index.coerceIn(AzkarContent.items(period).indices)); save(root)
    }
    fun increment(period: String, item: AzkarEntry) {
        val root = read(); val data = root.optJSONObject(period) ?: return
        val counts = data.optJSONObject("counts") ?: JSONObject().also { data.put("counts", it) }
        counts.put(item.id, (counts.optInt(item.id, 0).coerceAtLeast(0) + 1).coerceAtMost(item.repetitions))
        save(root)
    }
    fun finish() {
        val root = read(); val data = root.optJSONObject(root.optString("last")) ?: return
        if (data.optBoolean("complete", true)) return
        val session = data.optString("sessionId")
        data.put("complete", true); save(root)
        if (session.isNotBlank()) Store.recordMaterialCompleted("azkar:$session")
    }
}
