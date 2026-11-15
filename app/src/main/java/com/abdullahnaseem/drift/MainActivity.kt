package com.abdullahnaseem.drift

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fully transparent bars; the default adds a light scrim behind 3-button navigation.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        setContent { DriftTheme { App() } }
    }
}

/** Keep read access to picked photos across reboots; returns only those we could keep. */
fun Context.keep(uris: List<Uri>): List<Uri> = uris.filter {
    runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
}

/** Give back access to photos no collection uses any more (the system caps persisted grants). */
fun Context.release(state: State, removed: kotlin.collections.Collection<String>) {
    val inUse = state.collections.flatMapTo(HashSet()) { it.photos }
    removed.filterNot { it in inUse }.forEach {
        runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(Store.load(ctx)) }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }

    /** Persist an edit, then let the engine react (a new schedule may apply right away). */
    fun commit(force: Set<Surface> = emptySet(), f: (State) -> State) {
        val before = state
        state = Store.update(ctx, f)
        ctx.release(state, before.collections.flatMap { it.photos })
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) { Engine.run(ctx, force) }
            state = Store.load(ctx)
            busy = false
        }
    }

    // Opening the app re-arms the alarm, which a force-stop clears.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Engine.run(ctx) }
        state = Store.load(ctx)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = editing,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
            },
            label = "nav",
        ) { id ->
            val c = id?.let { i -> state.collections.firstOrNull { it.id == i } }
            if (c == null) Home(
                state = state,
                busy = busy,
                onOpen = { editing = it },
                onCreate = { picked ->
                    val uris = ctx.keep(picked)
                    if (uris.isEmpty()) return@Home
                    val new = Collection(System.currentTimeMillis(), "", uris.map(Uri::toString))
                    commit { it.copy(collections = it.collections + new) }
                    editing = new.id
                },
                onToggle = { c2, on -> commit { s -> s.copy(collections = s.collections.map { if (it.id == c2.id) it.copy(enabled = on) else it }) } },
                onShuffle = { surfaces -> commit(force = surfaces) { it } },
            ) else Editor(
                collection = c,
                others = state.collections.size - 1,
                onChange = { new -> commit { s -> s.copy(collections = s.collections.map { if (it.id == new.id) new else it }) } },
                onDelete = {
                    editing = null
                    commit { s -> s.copy(collections = s.collections.filterNot { it.id == c.id }) }
                },
                onBack = { editing = null },
            )
        }
    }
}
