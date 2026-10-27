package com.zikrtime.app.data

/** A reference is not evidence of expert review. Original and translation are independent. */
data class ReviewEvidence(
    val reviewer: String,
    val reviewedContentVersion: Int,
    val reviewedAt: String? = null,
    val scope: String,
) {
    fun appliesTo(version: Int): Boolean = reviewer.isNotBlank() && scope.isNotBlank() && version > 0 && reviewedContentVersion == version
}

data class SourceInfo(
    val originalReference: String? = null,
    val translationAttribution: String? = null,
    val webUrl: String? = null,
    val originalReview: ReviewEvidence? = null,
    val translationReview: ReviewEvidence? = null,
    val contentVersion: Int = 1,
    val translationVersion: Int = 1,
) {
    companion object {
        fun fromJson(value: org.json.JSONObject?, contentVersion: Int = 1): SourceInfo {
            fun text(o: org.json.JSONObject?, key: String) = (o?.opt(key) as? String)?.takeIf { it.isNotBlank() }
            fun review(key: String): ReviewEvidence? {
                val o = value?.optJSONObject(key) ?: return null
                val version = (o.opt("version") as? Number)?.toInt()?.takeIf { it > 0 } ?: return null
                return ReviewEvidence(text(o, "reviewer") ?: return null, version,
                    text(o, "date"), text(o, "scope") ?: return null)
            }
            return SourceInfo(text(value, "reference"), text(value, "translation"), text(value, "url"),
                review("originalReview"), review("translationReview"), contentVersion,
                value?.optInt("translationVersion", 1) ?: 1)
        }
    }
    val reviewedOriginal get() = originalReview?.takeIf { it.appliesTo(contentVersion) }
    val reviewedTranslation get() = translationReview?.takeIf { it.appliesTo(translationVersion) }
    val safeWebUrl: String? get() = webUrl?.takeIf {
        runCatching { val u = java.net.URI(it); u.scheme == "https" && !u.host.isNullOrBlank() && u.userInfo == null }.getOrDefault(false)
    }
}
