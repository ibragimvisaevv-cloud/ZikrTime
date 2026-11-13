package com.zikrtime.app.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GuideScreenshotsTest {
    @Test fun everyGuideActionHasABundledRealImageAndAVisibleTarget() {
        val root = File("src/main/assets")
        val catalog = GuideScreenshots.parse(File(root, GuideScreenshots.MANIFEST).readText())
        assertEquals(30, catalog.values.sumOf { it.size })
        val images = catalog.values.flatten().map { it.assetPath }.distinct()
        images.forEach { path ->
            val bytes = File(root, path).readBytes()
            assertTrue("Empty capture: $path", bytes.size > 1000)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
            assertEquals("WEBP", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        }
        assertTrue("Guide pictures must stay lightweight", images.sumOf { File(root, it).length() } < 4 * 1024 * 1024)
    }

    @Test fun malformedImagePathsAndOffscreenTargetsCannotBeAccepted() {
        val manifest = File("src/main/assets", GuideScreenshots.MANIFEST).readText()
        for (path in listOf("https://example.com/image.webp", "../image.webp", "/image.webp")) {
            val changed = JSONObject(manifest)
            changed.getJSONArray("welcome").getJSONObject(0).put("image", path)
            assertTrue(runCatching { GuideScreenshots.parse(changed.toString()) }.isFailure)
        }
        val changed = JSONObject(manifest)
        changed.getJSONArray("welcome").getJSONObject(0).getJSONArray("target").put(3, 1.2)
        assertTrue(runCatching { GuideScreenshots.parse(changed.toString()) }.isFailure)
    }
}
