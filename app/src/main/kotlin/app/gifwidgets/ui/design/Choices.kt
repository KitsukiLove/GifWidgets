package app.gifwidgets.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.PixelShape
import app.gifwidgets.ui.theme.PixelifySans

// More drop-ins in the Kixki look, with the same names and the parameters the app uses from Material:
// AlertDialog (a little program window), FilterChip, TabRow / Tab, Checkbox and RadioButton.
// A screen only has to change its import. Plus CollapsibleSection, the folding block of the long screens.

private fun chipShape(pixel: Boolean) = if (pixel) PixelShape(3.dp) else RoundedCornerShape(8.dp)

/**
 * A dialog that looks like a window of the page: gradient title bar, framed body, hard shadow. The body scrolls
 * if it does not fit. [title] may be null (a thin bar is drawn instead).
 */
@Composable
fun GifDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .hardShadow(design.shadow, design.shadowOffset, shape, downOnly = true)
                    .clip(shape)
                    .background(scheme.surface, shape)
                    .border(design.borderWidth, design.outline, shape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(design.titleStart, design.titleEnd)))
                        .heightIn(min = if (title != null) 44.dp else 12.dp)
                        .padding(horizontal = 14.dp, vertical = if (title != null) 6.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides design.titleInk) {
                            ProvideTextStyle(MaterialTheme.typography.titleSmall) { title() }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(design.borderWidth).background(design.outline))
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false).padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** A framed pill with a text label that can be on or off (the page's chips). */
@Composable
fun GifChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = chipShape(design.isPixel)
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surface, shape)
            .border(if (selected) 3.dp else 2.dp, (if (selected) scheme.primary else design.outline).copy(alpha = alpha), shape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = {
                KixkiAudio.blip(660.0, .05)
                onClick()
            })
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface.copy(alpha = alpha)) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}

/** The strip of tabs: equal-width framed buttons, the chosen one filled. [selectedTabIndex] is only informative here. */
@Composable
fun GifTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun RowScope.GifTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable () -> Unit,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = chipShape(design.isPixel)
    val fg = if (selected) scheme.onPrimary else scheme.onSurface
    Box(
        modifier = modifier
            .weight(1f)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surface, shape)
            .border(if (selected) 3.dp else 2.dp, if (selected) design.outline else design.outline.copy(alpha = 0.6f), shape)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = {
                KixkiAudio.blip(760.0, .05)
                onClick()
            })
            .heightIn(min = 44.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { text() }
        }
    }
}

/** A square (Pixel) or rounded (XP) box with a pixel tick. Pass a null [onCheckedChange] when the whole row is clickable. */
@Composable
fun GifCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (design.isPixel) PixelShape(2.dp) else RoundedCornerShape(5.dp)
    val alpha = if (enabled) 1f else 0.4f
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = {
            KixkiAudio.blip(if (it) 880.0 else 440.0, .05)
            onCheckedChange(it)
        })
    } else {
        Modifier
    }
    Box(modifier = modifier.size(48.dp).then(toggle), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(shape)
                .background((if (checked) scheme.primary else scheme.surface).copy(alpha = alpha), shape)
                .border(2.dp, design.outline.copy(alpha = alpha), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                val tick = scheme.onPrimary.copy(alpha = alpha)
                Canvas(Modifier.size(18.dp)) {
                    // A tick made of squares on a 6 x 6 grid.
                    val c = size.width / 6f
                    val cells = listOf(0 to 3, 1 to 4, 2 to 5, 3 to 4, 4 to 3, 5 to 2, 5 to 1, 5 to 0)
                    for ((x, y) in cells) drawRect(tick, Offset(x * c, y * c), Size(c, c))
                }
            }
        }
    }
}

/** A square (Pixel) or round (XP) mark that is filled when [selected]. */
@Composable
fun GifRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (design.isPixel) PixelShape(2.dp) else RoundedCornerShape(50)
    val alpha = if (enabled) 1f else 0.4f
    val choose = if (onClick != null) {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = {
            KixkiAudio.blip(700.0, .05)
            onClick()
        })
    } else {
        Modifier
    }
    Box(modifier = modifier.size(48.dp).then(choose), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(shape)
                .background(scheme.surface.copy(alpha = alpha), shape)
                .border(2.dp, design.outline.copy(alpha = alpha), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(12.dp).background(scheme.primary.copy(alpha = alpha), if (design.isPixel) PixelShape(1.dp) else RoundedCornerShape(50)))
            }
        }
    }
}

/**
 * A block of a long screen that folds: tap the header to open or close it. Open / closed is remembered across
 * rotation. [summary] is a short note shown on the right while it is closed (for example the current value);
 * [onReset], when given, shows a small "Restablecer" button in the header while the block is open.
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    summary: String? = null,
    resetLabel: String = "",
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val shape = design.cardShape
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(design.shadow, design.shadowOffset, shape, downOnly = true)
            .clip(shape)
            .background(scheme.surface, shape)
            .border(design.borderWidth, design.outline, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (open) scheme.primaryContainer else scheme.surface)
                .clickable(role = Role.Button) {
                    KixkiAudio.blip(if (open) 520.0 else 700.0, .05)
                    open = !open
                }
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chevron(open = open, color = scheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!open && summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp).padding(end = 8.dp),
                )
            }
            if (open && onReset != null) {
                TextButton(onClick = onReset) { Text(resetLabel) }
            }
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(design.outline.copy(alpha = 0.5f)))
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** A pixel arrow: pointing right when closed, down when open. */
@Composable
private fun Chevron(open: Boolean, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val c = size.width / 8f
        // right arrow: columns narrowing to the right; down arrow: rows narrowing downwards
        if (open) {
            for (row in 0 until 4) {
                val inset = row.toFloat()
                drawRect(color, Offset((1f + inset) * c, (2f + row) * c), Size((6f - 2f * inset) * c, c))
            }
        } else {
            for (col in 0 until 4) {
                val inset = col.toFloat()
                drawRect(color, Offset((2f + col) * c, (1f + inset) * c), Size(c, (6f - 2f * inset) * c))
            }
        }
    }
}

/** A framed one-line text box for a name, with a small label above it. Gets the keyboard at once when [autoFocus]. */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = chipShape(design.isPixel)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            try {
                focus.requestFocus()
            } catch (e: IllegalStateException) {
                // not attached yet: the user can still tap the box
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(scheme.surfaceVariant.copy(alpha = 0.5f), shape)
                        .border(3.dp, design.outline, shape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}
