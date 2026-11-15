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
        // The history opens from the form's FAB with a container transform and closes back into it.
        checkNotNull(device.wait(Until.findObject(By.desc("Wygenerowane deklaracje")), ScreenTimeoutMillis)) {
            "History button not found."
        }.click()
        device.wait(Until.findObject(By.text("Wygenerowane deklaracje")), ScreenTimeoutMillis)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
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

    @Test
    fun readNews() = profile.collect(
        packageName = PackageName,
        maxIterations = 6,
        stableIterations = 2,
    ) {
        startActivityAndWait()
        val width = device.displayWidth
        val height = device.displayHeight

        openTab("Więcej")
        // The card shows the latest articles once the feed is in; each one opens the article screen.
        val all = checkNotNull(device.wait(Until.findObject(By.text("Wszystkie aktualności")), ScreenTimeoutMillis)) {
            "News card not found."
        }
        checkNotNull(device.wait(Until.findObject(ArticleDate), FeedTimeoutMillis)) { "News feed did not load." }
        device.findObject(ArticleDate).click()
        device.waitForIdle()
        device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        checkNotNull(device.wait(Until.findObject(By.text("Wszystkie aktualności")), ScreenTimeoutMillis) ?: all).click()
        checkNotNull(device.wait(Until.findObject(ArticleDate), ScreenTimeoutMillis)) { "News list is empty." }.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun generateDeclaration() = profile.collect(
        packageName = PackageName,
        maxIterations = 4,
        stableIterations = 2,
    ) {
        startActivityAndWait()
        openTab("Deklaracja")
        // Three quick taps on the first section title fill the form with sample data in test builds.
        val section = checkNotNull(device.wait(Until.findObject(By.text("Twoje dane")), ScreenTimeoutMillis)) {
            "Declaration form not found."
        }
        repeat(3) { section.click() }
        checkNotNull(device.wait(Until.findObject(By.text("Jan Kowalski")), ScreenTimeoutMillis)) {
            "Sample data was not filled in."
        }
        device.findObject(By.text("Generuj PDF")).click()
        checkNotNull(device.wait(Until.findObject(By.desc("Widok PDF")), PdfTimeoutMillis)) { "PDF preview did not open." }
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // The same declaration again, from the history.
        device.findObject(By.desc("Wygenerowane deklaracje")).click()
        checkNotNull(device.wait(Until.findObject(By.text("Dotknij, aby wyświetlić podgląd")), ScreenTimeoutMillis)) {
            "History is empty."
        }.click()
        device.wait(Until.findObject(By.desc("Widok PDF")), PdfTimeoutMillis)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
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
        const val FeedTimeoutMillis = 15_000L
        const val PdfTimeoutMillis = 15_000L
        val Cluster = By.desc(Pattern.compile("\\d+ parafi.*"))
        /** An article teaser, found by its date, e.g. "21 października 2026". */
        val ArticleDate = By.text(Pattern.compile("\\d{1,2} \\p{L}+ \\d{4}"))
    }
}
