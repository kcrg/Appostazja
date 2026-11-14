package com.tryniecki.appostazja.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Keeps the map composed while other tabs are shown, so returning to it does not create a new MapView,
 * GL context and clustering pass. While hidden its lifecycle is capped at CREATED: the MapView stops
 * rendering exactly as when the app goes to the background, flows collected with the lifecycle pause and
 * back handlers inside stay inactive.
 */
@Composable
internal fun PersistentMapHost(visible: Boolean, content: @Composable () -> Unit) {
    // Stop only after the tab transition has covered the map, so it never freezes mid-fade.
    var active by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            active = true
        } else {
            delay(HIDE_DELAY_MS)
            active = false
        }
    }

    val parent = LocalLifecycleOwner.current
    val owner = remember { CappedLifecycleOwner() }
    DisposableEffect(parent, active) {
        val cap = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        val observer = LifecycleEventObserver { source, _ -> owner.follow(source.lifecycle.currentState, cap) }
        parent.lifecycle.addObserver(observer)
        owner.follow(parent.lifecycle.currentState, cap)
        onDispose { parent.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}

private class CappedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun follow(parentState: Lifecycle.State, cap: Lifecycle.State) {
        val target = minOf(parentState, cap)
        // A registry cannot return to INITIALIZED or leave DESTROYED.
        if (target == Lifecycle.State.INITIALIZED || registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = target
    }
}

private const val HIDE_DELAY_MS = 500L
