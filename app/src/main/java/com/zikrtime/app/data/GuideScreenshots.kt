package com.zikrtime.app.data

import org.json.JSONObject

/** Bounds come from the UI hierarchy captured with each real screenshot. */
data class GuideTarget(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f })
        require(right > left && bottom > top)
    }
}

data class GuideScreenshot(val image: String, val width: Int, val height: Int, val target: GuideTarget) {
    init {
        // The guide is an offline asset, never an arbitrary URL or filesystem path.
        require(image.matches(Regex("[a-z0-9][a-z0-9_-]*\\.webp")))
        require(width in 1..4096 && height in 1..8192)
    }
    val aspectRatio: Float get() = width.toFloat() / height
    val assetPath: String get() = "guide/ru/$image"
}

object GuideScreenshots {
    const val MANIFEST = "guide/ru/manifest.json"
    val topics = listOf("welcome", "prayer", "journal", "quran", "limits", "permissions",
        "knowledge", "places", "tools", "shop")

    fun parse(text: String): Map<String, List<GuideScreenshot>> {
        val json = JSONObject(text)
        return topics.associateWith { key ->
            val steps = json.getJSONArray(key)
            require(steps.length() == 3) { "Expected three screenshot steps for $key" }
            (0 until steps.length()).map { index ->
                val frame = steps.getJSONObject(index)
                val target = frame.getJSONArray("target")
                require(target.length() == 4)
                GuideScreenshot(frame.getString("image"), frame.getInt("width"), frame.getInt("height"),
                    GuideTarget(target.getDouble(0).toFloat(), target.getDouble(1).toFloat(),
                        target.getDouble(2).toFloat(), target.getDouble(3).toFloat()))
            }
        }
    }
}
