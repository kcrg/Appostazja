package com.tryniecki.appostazja.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val profile = BaselineProfileRule()

    @Test
    fun mapStartup() = profile.collect(
        packageName = PackageName,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        checkNotNull(device.wait(Until.findObject(Cluster), MapTimeoutMillis)) {
            "Map clusters did not load. Check the API, ADB reverse and MAPS_API_KEY."
        }
    }

    @Test
    fun exploreMap() = profile.collect(
        packageName = PackageName,
        maxIterations = 6,
        stableIterations = 2,
    ) {
        startActivityAndWait()
        checkNotNull(device.wait(Until.findObject(Cluster), MapTimeoutMillis)) {
            "Map clusters did not load. Check the API, ADB reverse and MAPS_API_KEY."
        }
        val width = device.displayWidth
        val height = device.displayHeight
        val cluster = device.findObjects(Cluster).firstOrNull {
            val bounds = it.visibleBounds
            bounds.centerX() in width / 8..width * 7 / 8 &&
                bounds.centerY() in height / 4..height * 3 / 4
        }
        checkNotNull(cluster) { "No tappable cluster in the center of the map." }.click()
        device.waitForIdle()
        device.swipe(width * 2 / 3, height / 2, width / 3, height * 3 / 5, 20)
        device.waitForIdle()
    }

    private companion object {
        const val PackageName = "com.tryniecki.appostazja"
        const val MapTimeoutMillis = 20_000L
        val Cluster = By.desc(Pattern.compile("\\d+ parafi.*"))
    }
}
