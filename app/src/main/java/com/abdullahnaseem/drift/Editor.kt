package com.abdullahnaseem.drift

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(
    collection: Collection,
    others: Int,
    onChange: (Collection) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val c = collection
    // The name is typed locally and saved on Done or on the way out, not per keystroke.
    var name by rememberSaveable(c.id) { mutableStateOf(c.name) }
    var selected by remember(c.id) { mutableStateOf(emptySet<String>()) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val latest by rememberUpdatedState(c)
    val latestName by rememberUpdatedState(name)
    DisposableEffect(c.id) {
        onDispose { if (latestName.trim() != latest.name) onChange(latest.copy(name = latestName.trim())) }
    }
    fun update(f: (Collection) -> Collection) = onChange(f(c))

    val pick = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        val add = ctx.keep(uris).map { it.toString() }.filterNot { it in c.photos }
        if (add.isNotEmpty()) update { it.copy(photos = it.photos + add) }
    }

    BackHandler { if (selected.isNotEmpty()) selected = emptySet() else onBack() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding(),
        ) {
            full {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTap(Glyph.Back, "Back", onBack)
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconTap(Glyph.More, "More", { menu = true })
                        DropdownMenu(menu, { menu = false }, containerColor = colors.surface, shape = Control) {
                            DropdownMenuItem(
                                text = { Text("Delete collection", color = colors.error) },
                                leadingIcon = { Icon(Glyph.Trash, null, tint = colors.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            full {
                Box(Modifier.padding(top = 20.dp, bottom = 28.dp)) {
                    val title = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp)
                    if (name.isEmpty()) Text("Name this collection", style = title, color = colors.onSurfaceVariant.copy(alpha = 0.55f))
                    BasicTextField(
                        value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                        textStyle = title.copy(color = colors.onBackground),
                        cursorBrush = SolidColor(Sun),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focus.clearFocus()
                            if (name.trim() != c.name) update { it.copy(name = name.trim()) }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            full {
                Column {
                    Label("Show on")
                    Segmented(listOf("Home", "Lock", "Both"), c.target.ordinal, { i -> update { it.copy(target = Target.entries[i]) } })

                    Hairline(Modifier.padding(vertical = 24.dp))
                    Label("Change every")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Debug builds get a 1-minute option so background rotation can be tested quickly.
                        val debug = ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
                        (if (debug) listOf(1 to "1 min") + Intervals else Intervals).forEach { (min, label) -> Chip(label, c.intervalMin == min) { update { it.copy(intervalMin = min) } } }
                    }

                    Hairline(Modifier.padding(vertical = 24.dp))
                    Label("Active")
                    Segmented(
                        listOf("Always", "On a schedule"), if (c.schedule == null) 0 else 1,
                        { i -> update { it.copy(schedule = if (i == 0) null else it.schedule ?: Schedule()) } },
                    )
                    AnimatedVisibility(c.schedule != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val s = c.schedule ?: Schedule()
                        Column(Modifier.padding(top = 18.dp)) {
                            Days(s.days) { d -> haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); update { it.copy(schedule = s.copy(days = d)) } }
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("All day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                DriftSwitch(s.allDay, { on ->
                                    update { it.copy(schedule = if (on) s.copy(start = 0, end = 0) else s.copy(start = 9 * 60, end = 17 * 60)) }
                                })
                            }
                            AnimatedVisibility(!s.allDay) {
                                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hours", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Time(timeText(ctx, s.start)) { editingTime = true }
                                    Text("–", Modifier.padding(horizontal = 8.dp), color = colors.onSurfaceVariant)
                                    Time(timeText(ctx, s.end)) { editingTime = false }
                                }
                            }
                            Text(
                                when {
                                    s.days == 0 -> "Pick at least one day."
                                    !s.allDay && s.end < s.start -> "Runs past midnight into the next day."
                                    others > 0 -> "Takes over from always-on collections during these hours."
                                    else -> "Outside these hours, your wallpaper stays as it is."
                                },
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    Hairline(Modifier.padding(top = 24.dp, bottom = 20.dp))
                    Label("${c.photos.size} photo${if (c.photos.size == 1) "" else "s"}")
                }
            }

            item {
                Box(
                    Modifier.aspectRatio(0.75f).press { pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                        .clip(Control).background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyph.Add, "Add photos", tint = colors.onSurfaceVariant) }
            }
            items(c.photos, key = { it }) { uri ->
                val on = uri in selected
                val scale by animateFloatAsState(if (on) 0.88f else 1f, label = "sel")
                Box(Modifier.aspectRatio(0.75f).animateItem().press {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    selected = if (on) selected - uri else selected + uri
                }) {
                    Photo(uri, Modifier.fillMaxSize().scale(scale).clip(Control))
                    if (on) Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(Sun),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Glyph.Check, null, tint = Ink, modifier = Modifier.size(16.dp)) }
                }
            }
            if (c.photos.isNotEmpty()) full {
                Text(
                    "Tap photos to select and remove them.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        // Selection bar
        AnimatedVisibility(
            selected.isNotEmpty(), Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(50)).background(colors.primary).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).press { selected = emptySet() }, contentAlignment = Alignment.Center) {
                    Icon(Glyph.Close, "Clear selection", tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                }
                Row(
                    Modifier.press {
                        val gone = selected
                        selected = emptySet()
                        update { it.copy(photos = it.photos - gone) }
                    }.clip(RoundedCornerShape(50)).background(colors.onPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Glyph.Trash, null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove ${selected.size}", style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
                }
            }
        }
    }

    editingTime?.let { start ->
        val s = c.schedule ?: Schedule()
        val initial = if (start) s.start else s.end
        val tp = rememberTimePickerState(initial / 60, initial % 60, android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { editingTime = null },
            containerColor = colors.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (start) "Starts at" else "Ends at", style = MaterialTheme.typography.titleMedium) },
            confirmButton = {
                TextButton({
                    val m = tp.hour * 60 + tp.minute
                    // Never let a picked time collapse the range into "all day".
                    val other = if (start) s.end else s.start
                    if (m != other) update { it.copy(schedule = if (start) s.copy(start = m) else s.copy(end = m)) }
                    editingTime = null
                }) { Text("Done", color = colors.onSurface) }
            },
            dismissButton = { TextButton({ editingTime = null }) { Text("Cancel", color = colors.onSurfaceVariant) } },
            text = {
                TimePicker(
                    tp, colors = TimePickerDefaults.colors(
                        clockDialColor = colors.surfaceVariant, selectorColor = Sun,
                        clockDialSelectedContentColor = Ink, clockDialUnselectedContentColor = colors.onSurface,
                        timeSelectorSelectedContainerColor = Sun, timeSelectorSelectedContentColor = Ink,
                        timeSelectorUnselectedContainerColor = colors.surfaceVariant, timeSelectorUnselectedContentColor = colors.onSurface,
                        periodSelectorSelectedContainerColor = Sun, periodSelectorSelectedContentColor = Ink,
                        periodSelectorUnselectedContentColor = colors.onSurfaceVariant, periodSelectorBorderColor = colors.outline,
                    )
                )
            },
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Delete “${c.name.ifBlank { "Untitled" }}”?", style = MaterialTheme.typography.titleLarge) },
        text = { Text("Your photos stay in your gallery.", color = colors.onSurfaceVariant) },
        confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete", color = colors.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = colors.onSurface) } },
    )
}

private fun LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Days(days: Int, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        names.forEachIndexed { i, day ->
            val on = days and (1 shl i) != 0
            Box(
                Modifier.size(38.dp).press { onChange(days xor (1 shl i)) }.clip(CircleShape)
                    .background(if (on) colors.primary else colors.background)
                    .border(1.dp, if (on) colors.primary else colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.take(1), style = MaterialTheme.typography.labelMedium,
                    color = if (on) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.semanticsLabel("$day, ${if (on) "on" else "off"}"),
                )
            }
        }
    }
}

private fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }

@Composable
private fun Time(text: String, onClick: () -> Unit) = Text(
    text, style = MaterialTheme.typography.bodyLarge.merge(Tnum),
    modifier = Modifier.press(onClick).clip(Control).background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 12.dp, vertical = 8.dp),
)
