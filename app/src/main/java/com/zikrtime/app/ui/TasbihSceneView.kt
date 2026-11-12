package com.zikrtime.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.core.view.doOnLayout
import com.zikrtime.app.BuildConfig
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import org.json.JSONObject
import java.io.ByteArrayInputStream

internal data class TasbihSceneConfig(
    val arabic: String,
    val transcription: String,
    val translation: String,
    val goal: Int,
    val lang: String,
    val reducedMotion: Boolean,
    val tapLabel: String,
    val unlimitedLabel: String,
    val completedLabel: String,
) {
    fun json(count: Int): JSONObject = JSONObject()
        .put("arabic", arabic).put("transcription", transcription).put("translation", translation)
        .put("goal", goal).put("count", count).put("lang", lang).put("reducedMotion", reducedMotion)
        .put("tapLabel", tapLabel).put("unlimitedLabel", unlimitedLabel).put("completedLabel", completedLabel)
}

/** Store owns every count. The local page only animates counts that Store has already accepted. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class TasbihSceneView(context: Context) : WebView(context) {
    private var config: TasbihSceneConfig? = null
    private var lastTotal = 0
    private var pageReady = false
    private var acceptingInput = false
    private var active = false
    private var disposed = false

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private var soundReady = false
    private var waitingStrikes = 0
    private val beadSample: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            soundReady = status == 0
            if (soundReady && active && !disposed && Store.tasbihSoundEnabled) {
                repeat(waitingStrikes) { playBead() }
            }
            waitingStrikes = 0
        }
        beadSample = pool.load(context, R.raw.tasbih_bead, 1)
        setBackgroundColor(Color.rgb(16, 24, 26))
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        webViewClient = SceneClient()
        addJavascriptInterface(SceneActions(), "AndroidTasbih")
        // The scene uses viewport-relative heights. A wrap-content WebView resolves those
        // against its document (which has only absolutely positioned children) instead.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        doOnLayout { if (!disposed) loadUrl(PAGE) }
    }

    fun bind(newConfig: TasbihSceneConfig, total: Int, acceptingInput: Boolean) {
        if (this.acceptingInput != acceptingInput) {
            this.acceptingInput = acceptingInput
            importantForAccessibility = if (acceptingInput) IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val changed = config != newConfig
        val externalChange = lastTotal != total
        config = newConfig
        lastTotal = total
        if (!pageReady || disposed) return
        if (changed) configurePage()
        else if (externalChange) evaluateJavascript("window.tasbih.updateCount($total,${newConfig.goal})", null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (pageReady && !disposed) setPageViewport(w, h)
    }

    private fun setPageViewport(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            evaluateJavascript("window.tasbih.setViewport($widthPx,$heightPx)", null)
        }
    }

    private fun configurePage() {
        config?.let {
            evaluateJavascript("window.tasbih.configure(${it.json(lastTotal)})", null)
        }
    }

    fun resumeScene() {
        if (disposed) return
        active = true
        onResume()
    }

    fun pauseScene() {
        active = false
        waitingStrikes = 0
        if (pageReady && !disposed) evaluateJavascript("window.tasbih.settle()", null)
        onPause()
    }

    fun releaseScene() {
        disposed = true
        active = false
        pool.release()
        removeJavascriptInterface("AndroidTasbih")
        stopLoading()
        destroy()
    }

    private fun playBead() {
        pool.play(beadSample, 1f, 1f, 1, 0, 1f)
    }

    @Keep
    private inner class SceneActions {
        @JavascriptInterface
        fun tap() {
            // JavascriptInterface calls arrive on a WebView thread; Store/widget writes are on main.
            post {
                if (!active || disposed || !pageReady || !acceptingInput) return@post
                Store.addTasbih()
                val total = Store.tasbihCount
                val goal = Store.tasbihTarget
                lastTotal = total
                evaluateJavascript("window.tasbih.step($total,$goal)", null)
                when {
                    goal > 0 && total % goal == 0 -> Haptics.done(context)
                    total % 33 == 0 -> Haptics.lap(context)
                    else -> Haptics.tick(context)
                }
            }
        }

        @JavascriptInterface
        fun strike() {
            post {
                if (!active || disposed || !Store.tasbihSoundEnabled) return@post
                if (soundReady) playBead() else waitingStrikes++
            }
        }
    }

    /** Same-origin app resources also allow the scene to reuse the existing Arabic font. */
    private inner class SceneClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            val uri = request.url
            val type = ASSETS[uri.path]
            if (uri.scheme == "https" && uri.encodedAuthority == HOST && type != null &&
                request.method == "GET" && (!request.isForMainFrame || uri.toString() == PAGE)) {
                val input = if (type == "font/ttf") resources.openRawResource(R.font.amiri)
                    else context.assets.open(uri.path!!.removePrefix("/"))
                return WebResourceResponse(type, if (type.startsWith("text/")) "UTF-8" else null, input)
            }
            return WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
                ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (disposed || url != PAGE) return
            pageReady = true
            setPageViewport(width, height)
            configurePage()
        }
    }

    private companion object {
        const val HOST = "appassets.androidplatform.net"
        const val PAGE = "https://$HOST/tasbih/scene.html"
        val ASSETS = mapOf(
            "/tasbih/scene.html" to "text/html",
            "/tasbih/scene.css" to "text/css",
            "/tasbih/scene.js" to "text/javascript",
            "/tasbih/nebula.webp" to "image/webp",
            "/tasbih/amiri.ttf" to "font/ttf",
        )
    }
}
