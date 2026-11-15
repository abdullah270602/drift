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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

// ── Palette: quiet neutrals so the photos carry all the colour. One warm accent, from the icon's sun.

val Sun = Color(0xFFE8A87C)
val Ink = Color(0xFF1A1816)
val Cream = Color(0xFFF2EFE8)

// "Selected" looks one way everywhere: primary fill, onPrimary text. Peach is reserved for "on".
private val Light = lightColorScheme(
    primary = Ink, onPrimary = Cream,
    background = Color(0xFFF7F5F1), onBackground = Ink,
    surface = Color(0xFFFFFFFF), onSurface = Ink,
    surfaceVariant = Color(0xFFEDEAE4), onSurfaceVariant = Color(0xFF77716A),
    surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFEDEAE4),
    outline = Color(0x1F1A1816), outlineVariant = Color(0x141A1816),
    error = Color(0xFFC4352B), tertiary = Color(0xFFB0602F), // deeper peach for text on white
)

private val Dark = darkColorScheme(
    primary = Cream, onPrimary = Ink,
    background = Color(0xFF0E0D0C), onBackground = Cream,
    surface = Color(0xFF1A1816), onSurface = Cream,
    surfaceVariant = Color(0xFF26231F), onSurfaceVariant = Color(0xFF9A948C),
    surfaceContainerHigh = Color(0xFF1A1816), surfaceContainerHighest = Color(0xFF26231F),
    outline = Color(0x1AF2EFE8), outlineVariant = Color(0x0FF2EFE8),
    error = Color(0xFFFF7A6B), tertiary = Sun,
)

// Pixel ships Google Sans as named system families; elsewhere these resolve to the default sans.
// Single variable typefaces, so Compose applies weights natively instead of faking bold.
private val Display = FontFamily(android.graphics.Typeface.create("google-sans", android.graphics.Typeface.NORMAL))
private val Text = FontFamily(android.graphics.Typeface.create("google-sans-text", android.graphics.Typeface.NORMAL))

@Composable
fun DriftTheme(content: @Composable () -> Unit) {
    val b = MaterialTheme.typography
    fun TextStyle.d() = copy(fontFamily = Display)
    fun TextStyle.t() = copy(fontFamily = Text)
    val type = b.copy(
        displaySmall = b.displaySmall.d().copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp),
        headlineSmall = b.headlineSmall.d().copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
        titleLarge = b.titleLarge.d().copy(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
        titleMedium = b.titleMedium.d().copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        bodyLarge = b.bodyLarge.t().copy(fontSize = 16.sp, letterSpacing = 0.sp),
        bodyMedium = b.bodyMedium.t().copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = b.bodySmall.t().copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
        labelLarge = b.labelLarge.d().copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelMedium = b.labelMedium.t().copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
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
    val More = glyph("M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zm12,0c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zm-6,0c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
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

// ── Controls. Two radii only: 14dp for controls, 24dp for photos and cards.

val Control = RoundedCornerShape(14.dp)
val Card = RoundedCornerShape(24.dp)

/** Segmented control with a sliding thumb. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).clip(Control).background(c.surfaceVariant).padding(3.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.82f, stiffness = 520f), label = "thumb")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(c.primary))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val tint by animateColorAsState(if (i == selected) c.onPrimary else c.onSurfaceVariant, label = "tint")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) {
                        if (i != selected) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(i) }
                    },
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
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (selected) c.primary else c.surfaceVariant, label = "bg")
    val fg by animateColorAsState(if (selected) c.onPrimary else c.onSurface, label = "fg")
    Box(
        Modifier.press { if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() }
            .clip(Control).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = fg) }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) = Text(
    text, modifier.padding(bottom = 10.dp),
    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

/** Peach means "on". */
@Composable
fun DriftSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Switch(
        checked, { haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff); onChange(it) },
        modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Sun, checkedThumbColor = Ink, checkedBorderColor = Sun,
            uncheckedTrackColor = c.surfaceVariant, uncheckedBorderColor = c.surfaceVariant, uncheckedThumbColor = c.onSurfaceVariant,
        ),
    )
}

/** Tabular digits so times don't jiggle. */
val Tnum = TextStyle(fontFeatureSettings = "tnum")
