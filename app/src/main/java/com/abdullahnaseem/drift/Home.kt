package com.abdullahnaseem.drift

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime

@Composable
fun Home(
    state: State,
    busy: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: (List<Uri>) -> Unit,
    onToggle: (Collection, Boolean) -> Unit,
    onShuffle: () -> Unit,
) {
    val pick = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { if (it.isNotEmpty()) onCreate(it) }
    val add = { pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val now = LocalDateTime.now()
    val live = Surface.entries.mapNotNull { state.activeFor(it, now)?.id }.toSet()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item {
                Text("Drift", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
            }
            if (state.collections.isEmpty()) {
                item { Empty() }
            } else {
                item { NowShowing(state, now, busy, onShuffle) }
                item { SectionLabel("Collections", Modifier.padding(top = 18.dp)) }
                items(state.collections, key = { it.id }) { c ->
                    CollectionCard(c, c.id in live, { onOpen(c.id) }, { onToggle(c, it) }, Modifier.animateItem())
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 20.dp)
                .press(add).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
                .padding(start = 20.dp, end = 24.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyph.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("New collection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun Empty() {
    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FannedCards()
        Spacer(Modifier.height(36.dp))
        Text("Your photos, always in motion", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Pick a few favourites and Drift will quietly change your wallpaper through the day.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** The icon's three drifting cards, used as the empty-state illustration. */
@Composable
private fun FannedCards() {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(150.dp, 170.dp), contentAlignment = Alignment.Center) {
        listOf(-22f to 0.12f, -11f to 0.28f).forEach { (deg, a) ->
            Box(
                Modifier.size(84.dp, 124.dp)
                    .rotateAbout(deg)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.onBackground.copy(alpha = a))
            )
        }
        // Same colours as the launcher icon in both themes, so the brand reads consistently.
        Box(Modifier.size(84.dp, 124.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F1EA))) {
            Box(Modifier.padding(start = 50.dp, top = 22.dp).size(18.dp).clip(CircleShape).background(Sun))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp).background(Color(0xFF14161A)))
        }
    }
}

private fun Modifier.rotateAbout(deg: Float) = graphicsLayer {
    rotationZ = deg
    transformOrigin = TransformOrigin(0.5f, 1.25f)
}

@Composable
private fun NowShowing(state: State, now: LocalDateTime, busy: Boolean, onShuffle: () -> Unit) {
    val names = state.collections.associate { it.id to it.name.ifBlank { "Untitled" } }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Surface.HOME to "Home", Surface.LOCK to "Lock").forEach { (s, label) ->
                val shown = state.shown[s]
                val active = state.activeFor(s, now)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Photo(
                        shown?.photo, px = 480,
                        modifier = Modifier.width(132.dp).aspectRatio(9f / 19.5f).clip(RoundedCornerShape(22.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        active?.let { names[it.id] } ?: "Not set",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(132.dp), textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        val spin by rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "deg",
        )
        Row(
            Modifier.press(onShuffle).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyph.Shuffle, null, Modifier.size(18.dp).rotate(if (busy) spin else 0f))
            Spacer(Modifier.width(8.dp))
            Text("Shuffle now", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CollectionCard(c: Collection, live: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().press(onOpen).clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = c.photos.take(4)
            repeat(4) { i ->
                Box(Modifier.weight(1f).aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))) {
                    Photo(shown.getOrNull(i), Modifier.fillMaxSize())
                    if (i == 3 && c.photos.size > 4) Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+${c.photos.size - 3}", style = MaterialTheme.typography.titleMedium, color = Color.White) }
                }
            }
        }
        Row(Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (live) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Sun))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${c.photos.size} photo${if (c.photos.size == 1) "" else "s"} · ${targetText(c.target)} · ${intervalText(c.intervalMin)}",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(scheduleText(ctx, c.schedule), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
            Switch(
                checked = c.enabled, onCheckedChange = onToggle,
                colors = switchColors(),
            )
        }
    }
}
