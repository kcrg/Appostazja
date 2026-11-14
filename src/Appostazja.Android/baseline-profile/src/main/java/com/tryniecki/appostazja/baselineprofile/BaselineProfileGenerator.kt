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

    @Test
    fun browseTabs() = profile.collect(
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

        openTab("Statystyki")
        checkNotNull(device.wait(Until.findObject(By.text("Jak przebiega apostazja")), ScreenTimeoutMillis)) {
            "Statistics did not load."
        }
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
        device.waitForIdle()

        openTab("Deklaracja")
        openTab("Więcej")
        openTab("Mapa")

        checkNotNull(device.wait(Until.findObject(By.desc("Szukaj parafii")), ScreenTimeoutMillis)) {
            "Search button not found."
        }.click()
        device.wait(Until.findObject(By.text("Najwięcej relacji")), ScreenTimeoutMillis)
        device.waitForIdle()
        device.pressBack() // keyboard
        device.pressBack() // search
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openTab(label: String) {
        checkNotNull(device.wait(Until.findObject(By.text(label)), ScreenTimeoutMillis)) { "Tab $label not found." }
            .click()
        device.waitForIdle()
    }

    private companion object {
        const val PackageName = "com.tryniecki.appostazja"
        const val MapTimeoutMillis = 20_000L
        const val ScreenTimeoutMillis = 10_000L
        val Cluster = By.desc(Pattern.compile("\\d+ parafi.*"))
    }
}
