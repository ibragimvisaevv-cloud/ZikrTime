package com.zikrtime.app.data

import org.json.JSONObject
import java.util.UUID

/** User-selected reading folders; no prescribed religious time boundaries or repetition counts. */
object AzkarStore {
    private const val KEY = "azkar_reading_v1"
    private fun read() = runCatching { JSONObject(Store.getRaw(KEY) ?: "{}") }.getOrElse { JSONObject() }
    private fun save(value: JSONObject) = Store.putRaw(KEY, value.toString())
    private fun active(root: JSONObject) = root.optJSONObject(root.optString("last", "morning")) ?: JSONObject()
    val pendingPeriod: String? get() = read().let { root ->
        listOf(root.optString("last"), "morning", "evening").distinct().firstOrNull { p ->
            p in setOf("morning", "evening") && root.optJSONObject(p)?.optBoolean("complete", true) == false
        }
    }
    val hasPending: Boolean get() = pendingPeriod != null
    val pendingIndex: Int get() = active(read()).optInt("index", 0).coerceIn(0, 5)
    fun start(period: String) {
        require(period == "morning" || period == "evening")
        val root = read(); root.put("last", period)
        if (root.optJSONObject(period)?.optBoolean("complete", true) != false)
            root.put(period, JSONObject().put("index", 0).put("complete", false).put("sessionId", UUID.randomUUID().toString()))
        save(root)
    }
    fun move(index: Int) { val root = read(); active(root).put("index", index.coerceIn(0, 5)); save(root) }
    fun finish() {
        val root = read(); val data = active(root)
        if (data.optBoolean("complete", true)) return
        val session = data.optString("sessionId")
        data.put("complete", true); save(root)
        if (session.isNotBlank()) Store.recordMaterialCompleted("azkar:$session")
    }
}
