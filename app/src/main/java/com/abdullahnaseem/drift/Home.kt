package com.abdullahnaseem.drift

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.util.Date

@Composable
fun Home(
    state: State,
    busy: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: (List<Uri>) -> Unit,
    onToggle: (Collection, Boolean) -> Unit,
    onShuffle: (Set<Surface>) -> Unit,
) {
    val pick = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { if (it.isNotEmpty()) onCreate(it) }
    val add = { pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val now = LocalDateTime.now()
    val live = Surface.entries.mapNotNull { state.activeFor(it, now)?.id }.toSet()

    if (state.collections.isEmpty()) {
        Empty(add)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
    ) {
        item { Header(state, now, busy) { onShuffle(Surface.entries.toSet()) } }
        item { NowShowing(state, now, onShuffle) }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Collections", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconTap(Glyph.Add, "New collection", add)
            }
        }
        items(state.collections, key = { it.id }) { c ->
            CollectionRow(c, c.id in live, { onOpen(c.id) }, { onToggle(c, it) }, Modifier.animateItem())
        }
    }
}

@Composable
fun IconTap(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).press(onClick).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun Header(state: State, now: LocalDateTime, busy: Boolean, onShuffle: () -> Unit) {
    val ctx = LocalContext.current
    val next = remember(state) { Engine.nextWake(state, now) }
    val anyOn = state.collections.any { it.enabled && it.photos.isNotEmpty() }
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Drift", style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 34.sp))
            Text(
                when {
                    !anyOn -> "Paused"
                    next == null -> "Nothing else scheduled"
                    else -> "Next change around ${DateFormat.getTimeFormat(ctx).format(Date(next))}"
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val spin by rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "deg",
        )
        Box(Modifier.rotate(if (busy) spin else 0f)) { IconTap(Glyph.Shuffle, "Shuffle both screens", onShuffle) }
    }
}

/** Two small phones, dressed just enough (clock, dock) to read as Lock and Home. Tap one to move it on. */
@Composable
private fun NowShowing(state: State, now: LocalDateTime, onShuffle: (Set<Surface>) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
        listOf(Surface.LOCK to "Lock", Surface.HOME to "Home").forEach { (s, label) ->
            val active = state.activeFor(s, now)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(120.dp).aspectRatio(9f / 19.5f).press { if (active != null) onShuffle(setOf(s)) }
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Crossfade(state.shown[s]?.photo, animationSpec = tween(450, easing = FastOutSlowInEasing), label = "wall") {
                        Photo(it, Modifier.fillMaxSize(), px = 420)
                    }
                    if (s == Surface.LOCK) Text(
                        DateFormat.format(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Date()).toString(),
                        Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Normal),
                        color = Color.White.copy(alpha = 0.92f),
                    ) else Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) { repeat(4) { Box(Modifier.size(13.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f))) } }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "$label · ${active?.name?.ifBlank { "Untitled" } ?: "Not set"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(120.dp),
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(c: Collection, live: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dim by animateFloatAsState(if (c.enabled) 1f else 0.45f, label = "dim")
    Row(
        modifier.fillMaxWidth().press(onOpen).clip(Card).background(colors.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stack(c.photos, Modifier.alpha(dim))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp).alpha(dim)) {
            Text(c.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                "${c.photos.size} photo${if (c.photos.size == 1) "" else "s"} · ${targetText(c.target)} · ${intervalLabel(c.intervalMin)}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (live || c.schedule != null) Text(
                buildString {
                    if (live) append("On now")
                    if (live && c.schedule != null) append(" · ")
                    if (c.schedule != null) append(scheduleText(ctx, c.schedule))
                },
                style = MaterialTheme.typography.bodySmall, color = if (live) Sun else colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        DriftSwitch(c.enabled, onToggle)
    }
}

/** Up to three photos fanned like the app icon. */
@Composable
private fun Stack(photos: List<String>, modifier: Modifier = Modifier) {
    Box(modifier.size(72.dp, 80.dp), contentAlignment = Alignment.CenterEnd) {
        val shown = photos.take(3).reversed()
        shown.forEachIndexed { i, uri ->
            val depth = shown.size - 1 - i
            Photo(
                uri,
                Modifier.size(46.dp, 66.dp)
                    .graphicsLayer {
                        rotationZ = -9f * depth; translationX = -5.dp.toPx() * depth
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(10.dp)),
                px = 160,
            )
        }
    }
}

@Composable
private fun Empty(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        FannedCards()
        Spacer(Modifier.height(40.dp))
        Text("Start with a few photos", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Drift changes your wallpaper quietly through the day.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(32.dp))
        Row(
            Modifier.press(onAdd).clip(RoundedCornerShape(50)).background(Sun)
                .padding(start = 20.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyph.Add, null, tint = Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Choose photos", style = MaterialTheme.typography.labelLarge, color = Ink)
        }
    }
}

/** The icon's three cards, fanning out once when the screen appears. */
@Composable
private fun FannedCards() {
    val c = MaterialTheme.colorScheme
    val fan = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fan.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessVeryLow)) }
    Box(Modifier.size(150.dp, 160.dp), contentAlignment = Alignment.Center) {
        listOf(-22f to 0.12f, -11f to 0.28f).forEach { (deg, a) ->
            Box(
                Modifier.size(84.dp, 124.dp)
                    .graphicsLayer { rotationZ = deg * fan.value; transformOrigin = TransformOrigin(0.5f, 1.25f) }
                    .clip(RoundedCornerShape(16.dp)).background(c.onBackground.copy(alpha = a))
            )
        }
        // Same colours and hill as the launcher icon, in both themes.
        Canvas(Modifier.size(84.dp, 124.dp).clip(RoundedCornerShape(16.dp))) {
            val u = size.width / 30f // the icon's card is 30 units wide
            drawRect(Cream)
            drawCircle(Sun, radius = 4 * u, center = Offset(22 * u, 12 * u))
            drawPath(Path().apply {
                moveTo(0f, 31 * u)
                cubicTo(6 * u, 25 * u, 12 * u, 25 * u, 17 * u, 29 * u)
                cubicTo(22 * u, 33 * u, 26 * u, 31 * u, 30 * u, 26 * u)
                lineTo(30 * u, size.height); lineTo(0f, size.height); close()
            }, Ink)
        }
    }
}
