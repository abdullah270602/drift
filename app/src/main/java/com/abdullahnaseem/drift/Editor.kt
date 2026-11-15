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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

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
    val c = collection
    // The name is typed locally and saved on the way out, so we don't hit disk per keystroke.
    var name by rememberSaveable(c.id) { mutableStateOf(c.name) }
    var selected by remember(c.id) { mutableStateOf(emptySet<String>()) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var confirmDelete by remember { mutableStateOf(false) }

    val latest by rememberUpdatedState(c)
    val latestName by rememberUpdatedState(name)
    DisposableEffect(c.id) {
        onDispose { if (latestName.trim() != latest.name) onChange(latest.copy(name = latestName.trim())) }
    }
    fun update(f: (Collection) -> Collection) = onChange(f(c))

    val pick = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        ctx.keep(uris)
        val add = uris.map { it.toString() }.filterNot { it in c.photos }
        if (add.isNotEmpty()) update { it.copy(photos = it.photos + add) }
    }

    BackHandler { if (selected.isNotEmpty()) selected = emptySet() else onBack() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding(),
        ) {
            full {
                Box(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Box(Modifier.size(44.dp).press(onBack).clip(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Glyph.Back, "Back", tint = colors.onBackground)
                    }
                }
            }
            full {
                Box(Modifier.padding(top = 4.dp, bottom = 18.dp)) {
                    if (name.isEmpty()) Text("Untitled", style = MaterialTheme.typography.displaySmall, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    BasicTextField(
                        value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                        textStyle = MaterialTheme.typography.displaySmall.copy(color = colors.onBackground),
                        cursorBrush = SolidColor(colors.onBackground),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            full {
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(16.dp)) {
                    SectionLabel("Show on")
                    Segmented(
                        listOf("Home", "Lock", "Both"), c.target.ordinal,
                        { i -> update { it.copy(target = Target.entries[i]) } },
                    )

                    SectionLabel("Change", Modifier.padding(top = 22.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Intervals.forEach { (min, label) ->
                            Pill(label, c.intervalMin == min) { update { it.copy(intervalMin = min) } }
                        }
                    }

                    SectionLabel("When", Modifier.padding(top = 22.dp))
                    Segmented(
                        listOf("Always", "On a schedule"), if (c.schedule == null) 0 else 1,
                        { i -> update { it.copy(schedule = if (i == 0) null else it.schedule ?: Schedule()) } },
                    )
                    AnimatedVisibility(c.schedule != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        val s = c.schedule ?: Schedule()
                        Column(Modifier.padding(top = 16.dp)) {
                            Days(s.days) { d -> update { it.copy(schedule = s.copy(days = d)) } }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("All day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(
                                    s.allDay,
                                    { on -> update { it.copy(schedule = if (on) s.copy(start = 0, end = 0) else s.copy(start = 9 * 60, end = 17 * 60)) } },
                                    colors = switchColors(),
                                )
                            }
                            AnimatedVisibility(!s.allDay) {
                                Column {
                                    TimeRow("From", timeText(ctx, s.start)) { editingTime = true }
                                    TimeRow("Until", timeText(ctx, s.end) + if (s.end < s.start) "  (next day)" else "") { editingTime = false }
                                }
                            }
                            Text(
                                if (others > 0) "While scheduled, this collection takes over from always-on ones."
                                else "Outside these hours, your wallpaper stays as it is.",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            full {
                Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("${c.photos.size} photo${if (c.photos.size == 1) "" else "s"}", Modifier.weight(1f))
                }
            }

            item {
                Box(
                    Modifier.aspectRatio(0.75f).press { pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
                        .clip(RoundedCornerShape(14.dp)).border(1.5.dp, colors.outline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyph.Add, "Add photos", tint = colors.onSurfaceVariant) }
            }
            items(c.photos, key = { it }) { uri ->
                val on = uri in selected
                val scale by animateFloatAsState(if (on) 0.88f else 1f, label = "sel")
                Box(Modifier.aspectRatio(0.75f).animateItem().press { selected = if (on) selected - uri else selected + uri }) {
                    Photo(uri, Modifier.fillMaxSize().scale(scale).clip(RoundedCornerShape(14.dp)))
                    if (on) Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Glyph.Check, null, tint = colors.onPrimary, modifier = Modifier.size(16.dp)) }
                }
            }

            full {
                Text(
                    "Delete collection", style = MaterialTheme.typography.labelLarge, color = colors.error,
                    modifier = Modifier.padding(top = 36.dp).press { confirmDelete = true }.padding(vertical = 12.dp),
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
                    }.clip(RoundedCornerShape(50)).background(colors.onPrimary.copy(alpha = 0.12f))
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
                        clockDialColor = colors.surfaceVariant, selectorColor = colors.primary,
                        timeSelectorSelectedContainerColor = colors.primary, timeSelectorSelectedContentColor = colors.onPrimary,
                        timeSelectorUnselectedContainerColor = colors.surfaceVariant,
                        periodSelectorSelectedContainerColor = colors.primary, periodSelectorSelectedContentColor = colors.onPrimary,
                        clockDialSelectedContentColor = colors.onPrimary,
                    )
                )
            },
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = colors.surface,
        title = { Text("Delete “${c.name.ifBlank { "Untitled" }}”?") },
        text = { Text("Your photos stay in your gallery. Only this collection goes.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete", color = colors.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = colors.onSurface) } },
    )
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Days(days: Int, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, label ->
            val on = days and (1 shl i) != 0
            Box(
                Modifier.size(40.dp).press { onChange(days xor (1 shl i)) }.clip(CircleShape)
                    .background(if (on) colors.primary else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) colors.onPrimary else colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).press(onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun switchColors() = MaterialTheme.colorScheme.let { c ->
    SwitchDefaults.colors(
        checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary,
        uncheckedTrackColor = c.surfaceVariant, uncheckedBorderColor = c.surfaceVariant, uncheckedThumbColor = c.onSurfaceVariant,
    )
}

