package com.abdullahnaseem.drift

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

// ── Palette: quiet neutrals so the photos carry all the colour. One warm accent, from the icon's sun.

val Sun = Color(0xFFE8A87C)

private val Light = lightColorScheme(
    primary = Color(0xFF141414), onPrimary = Color(0xFFFAF9F7),
    background = Color(0xFFF6F5F2), onBackground = Color(0xFF141414),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFEBE9E4), onSurfaceVariant = Color(0xFF6E6C67),
    surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFEBE9E4), surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFFD9D6D0), outlineVariant = Color(0xFFE6E3DE),
    error = Color(0xFFC4352B), tertiary = Sun,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2F1EE), onPrimary = Color(0xFF0C0C0D),
    background = Color(0xFF0C0C0D), onBackground = Color(0xFFF2F1EE),
    surface = Color(0xFF17171A), onSurface = Color(0xFFF2F1EE),
    surfaceVariant = Color(0xFF242428), onSurfaceVariant = Color(0xFF9B9993),
    surfaceContainerHigh = Color(0xFF1E1E21), surfaceContainerHighest = Color(0xFF2A2A2E), surfaceBright = Color(0xFF3A3A40),
    outline = Color(0xFF3A3A3F), outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFFF6B5E), tertiary = Sun,
)

@Composable
fun DriftTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val type = base.copy(
        displaySmall = base.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = type, content = content)
}

// ── Icons: six glyphs don't justify an icon library.

private fun glyph(d: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes(d), fill = SolidColor(Color.Black)).build()

object Glyph {
    val Back = glyph("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val Add = glyph("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Shuffle = glyph("M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zm0.33,9.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z")
    val Check = glyph("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val Close = glyph("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Trash = glyph("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
}

// ── Thumbnails: the system's own thumbnail cache first, a sampled decode as fallback.

object Thumbs {
    private val cache = object : LruCache<String, ImageBitmap>(16 shl 20) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(uri: String, px: Int): ImageBitmap? = cache.get("$px|$uri")

    suspend fun load(ctx: Context, uri: String, px: Int): ImageBitmap? = peek(uri, px) ?: withContext(Dispatchers.IO) {
        val u = Uri.parse(uri)
        runCatching { ctx.contentResolver.loadThumbnail(u, Size(px, px), null) }
            .recoverCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, u)) { d, info, _ ->
                    d.setTargetSampleSize(max(1, minOf(info.size.width, info.size.height) / px))
                }
            }
            .getOrNull()?.asImageBitmap()?.also { cache.put("$px|$uri", it) }
    }
}

@Composable
fun Photo(uri: String?, modifier: Modifier = Modifier, px: Int = 320) {
    val ctx = LocalContext.current
    val img by produceState(uri?.let { Thumbs.peek(it, px) }, uri, px) { value = uri?.let { Thumbs.load(ctx, it, px) } }
    val alpha by animateFloatAsState(if (img != null) 1f else 0f, tween(260), label = "fade")
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        img?.let {
            Image(it, null, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = ContentScale.Crop)
        }
    }
}

// ── Controls

/** iOS-style segmented control with a sliding thumb. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceVariant).padding(3.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.85f, stiffness = 500f), label = "thumb")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(c.surfaceBright))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val tint by animateColorAsState(if (i == selected) c.onSurface else c.onSurfaceVariant, label = "tint")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelMedium, color = tint) }
            }
        }
    }
}

/** A pressable modifier that gently scales down: the one bit of physicality used everywhere. */
@Composable
fun Modifier.press(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = 900f), label = "press")
    return scale(s).clickable(source, null, onClick = onClick)
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) c.primary else c.surfaceVariant, label = "bg")
    val fg by animateColorAsState(if (selected) c.onPrimary else c.onSurface, label = "fg")
    Box(
        Modifier.press(onClick).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = fg) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier.padding(bottom = 10.dp),
    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

// ── Wording, shared by the list and the editor.

val Intervals = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 180 to "3 hours", 360 to "6 hours", 720 to "12 hours", 1440 to "Daily")

fun intervalText(min: Int) = when (min) {
    1440 -> "daily"
    else -> "every " + (Intervals.firstOrNull { it.first == min }?.second ?: "$min min").replace("1 hour", "hour")
}

fun targetText(t: Target) = when (t) { Target.HOME -> "Home screen"; Target.LOCK -> "Lock screen"; Target.BOTH -> "Home & lock" }

private val DayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun daysText(days: Int) = when (days) {
    0b1111111 -> "Every day"
    0b0011111 -> "Weekdays"
    0b1100000 -> "Weekends"
    0 -> "No days"
    else -> DayNames.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(", ")
}

fun timeText(ctx: Context, minute: Int): String {
    val h = minute / 60; val m = minute % 60
    return if (android.text.format.DateFormat.is24HourFormat(ctx)) "%d:%02d".format(h, m)
    else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

fun scheduleText(ctx: Context, s: Schedule?) = when {
    s == null -> "Always"
    s.allDay -> daysText(s.days)
    else -> "${daysText(s.days)} · ${timeText(ctx, s.start)}–${timeText(ctx, s.end)}"
}
