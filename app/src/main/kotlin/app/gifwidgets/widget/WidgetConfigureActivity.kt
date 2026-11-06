package app.gifwidgets.widget

import app.gifwidgets.ui.design.ScreenWindow
import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import app.gifwidgets.library.ThumbnailStore
import app.gifwidgets.ui.design.SearchField
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import app.gifwidgets.ui.design.GifDialog
import app.gifwidgets.ui.design.Button
import app.gifwidgets.ui.design.CollapsibleSection
import app.gifwidgets.ui.design.TextButton
import app.gifwidgets.ui.AddNameDialog
import app.gifwidgets.ui.PendingGif
import app.gifwidgets.library.AnimationNames
import app.gifwidgets.library.GifImporter
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import app.gifwidgets.ui.design.GifCheckbox
import app.gifwidgets.ui.design.GifChip
import app.gifwidgets.ui.design.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import app.gifwidgets.ui.design.OutlinedButton
import app.gifwidgets.ui.design.GifRadio
import androidx.compose.material3.Scaffold
import app.gifwidgets.ui.design.Slider
import androidx.compose.material3.Surface
import app.gifwidgets.ui.design.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import app.gifwidgets.ui.design.KixkiCorner
import androidx.lifecycle.lifecycleScope
import app.gifwidgets.R
import app.gifwidgets.control.ControlStore
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.GifQuickAdd
import app.gifwidgets.library.HighQualityFrames
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.memory.BitmapFormat
import app.gifwidgets.memory.ScreenMetrics
import app.gifwidgets.ui.messageRes
import app.gifwidgets.ui.theme.AppTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-widget configuration (also used to reconfigure an existing widget).
 * RESULT_CANCELED until the user saves; RESULT_OK + EXTRA_APPWIDGET_ID only after the widget has had its
 * first full update (the provider's onUpdate is not called for a newly configured widget).
 */
class WidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val (widthPx, heightPx) = WidgetUpdater.sizePx(this, manager, appWidgetId)
        val widgetStore = WidgetStore(this)
        val appSettings = ControlStore(this).load()
        val initial = widgetStore.loadSettings(appWidgetId).let {
            // A new widget starts with the "Píxel nítido" default from Settings, and with the animation chosen
            // in the library when it came from "Añadir a pantalla de inicio" (null when added from the widget picker).
            if (widgetStore.hasSettings(appWidgetId)) {
                it
            } else {
                val pendingId = WidgetPinner.peekPending(applicationContext)
                val pendingMeta = pendingId?.let { id -> LibraryStore(applicationContext).read(id) }
                if (pendingMeta != null) {
                    it.copy(animationId = pendingMeta.id, sharp = WidgetSettings.forAnimation(pendingMeta, appSettings.sharpPixels).sharp)
                } else {
                    it.copy(sharp = appSettings.sharpPixels, animationId = pendingId ?: it.animationId)
                }
            }
        }

        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenWindow(
                        title = stringResource(R.string.win_widget),
                        onClose = { finish() },
                        actions = { KixkiCorner(stringArrayResource(R.array.tips_widget).toList()) },
                    ) {
                        ConfigureScreen(
                            lifecycle = lifecycle,
                            appWidgetId = appWidgetId,
                            initial = initial,
                            playOnceEnabled = appSettings.playOnceEnabled,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            onSave = { settings, done -> save(settings, done) },
                            onCancel = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun save(settings: WidgetSettings, done: () -> Unit) {
        val id = appWidgetId
        val app = applicationContext
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                val store = WidgetStore(app)
                store.saveSettings(id, settings)
                store.writeUsage(id, WidgetStore.DEFAULT_USAGE) // start again from the full budget
                WidgetPinner.clearPending(app)
                WidgetUpdater.update(app, AppWidgetManager.getInstance(app), id)
                RotationAlarm.arm(app)
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            done()
            finish()
        }
    }
}

private val PALETTE = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF5C8A.toInt(), 0xFF7C4DFF.toInt(),
    0xFF00BCD4.toInt(), 0xFFFFC107.toInt(), 0xFF4CAF50.toInt(),
)

@Composable
private fun ConfigureScreen(
    lifecycle: Lifecycle,
    appWidgetId: Int,
    initial: WidgetSettings,
    playOnceEnabled: Boolean,
    widthPx: Int,
    heightPx: Int,
    onSave: (WidgetSettings, () -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(initial) }
    var animations by remember { mutableStateOf<List<AnimationMeta>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var addProgress by remember { mutableFloatStateOf(0f) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    var hqBusy by remember { mutableStateOf(false) }
    var hqProgress by remember { mutableFloatStateOf(0f) }
    var hqMessage by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // "Elegir GIF": system picker -> quick name question -> import -> optimize with the defaults -> choose it for this widget.
    var askName by remember { mutableStateOf<PendingGif?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val fallbackName = context.getString(R.string.default_animation_name)
                val suggested = withContext(Dispatchers.IO) {
                    AnimationNames.suggest(GifImporter.displayName(context.applicationContext, uri), fallbackName)
                }
                askName = PendingGif(uri, suggested)
            }
        }
    }
    fun addPicked(item: PendingGif) {
        adding = true
        addProgress = 0f
        addMessage = null
        scope.launch {
            val fallbackName = context.getString(R.string.default_animation_name)
            val result = withContext(Dispatchers.Default) {
                GifQuickAdd.add(context.applicationContext, item.uri, fallbackName, onProgress = { addProgress = it }, nameOverride = item.name)
            }
            when (result) {
                is GifQuickAdd.Result.Success -> {
                    animations = withContext(Dispatchers.IO) { LibraryStore(context).list() }
                    val globalSharp = withContext(Dispatchers.IO) { ControlStore(context).load().sharpPixels }
                    settings = settings.copy(
                        animationId = result.meta.id,
                        sharp = WidgetSettings.forAnimation(result.meta, globalSharp).sharp,
                    ).sanitized()
                    addMessage = context.getString(R.string.wc_add_done, result.meta.name)
                }
                is GifQuickAdd.Result.ImportFailed -> addMessage = context.getString(result.reason.messageRes())
                is GifQuickAdd.Result.SaveFailed -> addMessage = context.getString(result.reason.messageRes())
            }
            adding = false
        }
    }
    askName?.let { item ->
        AddNameDialog(
            current = item,
            position = 1,
            total = 1,
            onConfirm = { name -> askName = null; addPicked(item.copy(name = name)) },
            onConfirmAll = { name -> askName = null; addPicked(item.copy(name = name)) },
            onCancel = { askName = null },
        )
    }

    // "Tamaño real" and "Copiar estilo de otro widget".
    var showReal by remember { mutableStateOf(false) }
    var realDark by remember { mutableStateOf(true) }
    var copyOpen by remember { mutableStateOf(false) }
    var copySources by remember { mutableStateOf<List<LookSource>?>(null) }
    var copiedFrom by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { LibraryStore(context).list() }
        animations = list
        // Pick the first optimized animation when none is chosen yet.
        if (settings.animationId == null) {
            list.firstOrNull { it.isOptimized }?.let { first ->
                val globalSharp = withContext(Dispatchers.IO) { ControlStore(context).load().sharpPixels }
                settings = settings.copy(
                    animationId = first.id,
                    sharp = WidgetSettings.forAnimation(first, globalSharp).sharp,
                )
            }
        }
        loaded = true
    }

    val chosen = animations.firstOrNull { it.id == settings.animationId && it.isOptimized }

    LaunchedEffect(settings, chosen) {
        val meta = chosen
        preview = if (meta == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val bmp: Bitmap? = WidgetRenderer.renderPreview(context.applicationContext, settings, meta, widthPx, heightPx)
                bmp?.asImageBitmap()
            }
        }
    }

    // The preview moves: a short loop drawn with the current look, rebuilt a moment after the last change.
    var previewLoop by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var previewMs by remember { mutableStateOf(100L) }
    var previewIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings, chosen) {
        val meta = chosen
        if (meta == null) {
            previewLoop = emptyList()
        } else {
            delay(350)
            val loop = withContext(Dispatchers.Default) {
                try {
                    WidgetRenderer.renderPreviewFrames(context.applicationContext, settings, meta, widthPx, heightPx)
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
            previewLoop = loop?.frames?.map { it.asImageBitmap() }.orEmpty()
            previewMs = loop?.frameMs ?: 100L
            previewIndex = 0
        }
    }
    LaunchedEffect(previewLoop, previewMs) {
        if (previewLoop.size > 1) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(previewMs)
                    previewIndex = (previewIndex + 1) % previewLoop.size
                }
            }
        }
    }

    fun update(change: (WidgetSettings) -> WidgetSettings) {
        settings = change(settings).sanitized()
    }

    if (showReal) {
        val density = LocalDensity.current.density
        val frame = previewLoop.getOrNull(previewIndex) ?: preview
        GifDialog(
            onDismissRequest = { showReal = false },
            title = { Text(stringResource(R.string.wc_real_size_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GifChip(selected = realDark, onClick = { realDark = true }, label = { Text(stringResource(R.string.wc_real_dark)) })
                        GifChip(selected = !realDark, onClick = { realDark = false }, label = { Text(stringResource(R.string.wc_real_light)) })
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (realDark) Color(0xFF15121F) else Color(0xFFEDE7F6))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (frame != null) {
                            Image(
                                bitmap = frame,
                                contentDescription = stringResource(R.string.wc_preview),
                                modifier = Modifier
                                    .widthIn(max = (widthPx / density).dp)
                                    .fillMaxWidth()
                                    .aspectRatio(widthPx.toFloat() / heightPx.coerceAtLeast(1)),
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.None,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.wc_real_size_hint, (widthPx / density).roundToInt(), (heightPx / density).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showReal = false }) { Text(stringResource(R.string.editor_ok)) } },
        )
    }

    if (copyOpen) {
        LaunchedEffect(Unit) {
            if (copySources == null) {
                copySources = withContext(Dispatchers.IO) { loadLookSources(context.applicationContext, appWidgetId) }
            }
        }
        GifDialog(
            onDismissRequest = { copyOpen = false },
            title = { Text(stringResource(R.string.wc_copy_look_title)) },
            text = {
                val list = copySources
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    when {
                        list == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        list.isEmpty() -> Text(stringResource(R.string.wc_copy_look_none))
                        else -> {
                            Text(stringResource(R.string.wc_copy_look_help), style = MaterialTheme.typography.bodySmall)
                            list.forEach { src ->
                                TextButton(
                                    onClick = {
                                        update { it.withLookFrom(src.settings) }
                                        copiedFrom = src.name
                                        copyOpen = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(src.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { copyOpen = false }) { Text(stringResource(R.string.wc_cancel)) } },
        )
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Pinned on top: the preview and the memory line stay in view while the options below scroll.
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Neutral grey backdrop so transparency is visible in the preview.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFF7A7A7A)),
                    contentAlignment = Alignment.Center,
                ) {
                    (previewLoop.getOrNull(previewIndex) ?: preview)?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.wc_preview),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
                if (chosen != null) {
                    val screen = remember { ScreenMetrics.physicalSizePx(context) }
                    val (bmpW, bmpH) = WidgetRenderer.bitmapSize(settings, chosen, widthPx, heightPx)
                    val plan = WidgetPlanner.plan(
                        chosen.outFrames, chosen.outFps, settings.speedPercent, bmpW, bmpH,
                        screen.width, screen.height, WidgetStore.DEFAULT_USAGE,
                        if (WidgetRenderer.canUseRgb565(settings, chosen)) BitmapFormat.RGB_565 else BitmapFormat.ARGB_8888,
                        smooth = WidgetRenderer.isSmoothKind(chosen),
                    )
                    Text(
                        text = if (settings.highQuality) {
                            stringResource(R.string.wc_hq_info)
                        } else if (plan.framesReduced || plan.sizeReduced) {
                            stringResource(R.string.wc_info_reduced, plan.keptFrames, chosen.outFrames, plan.width, plan.height, plan.intervalMs)
                        } else {
                            stringResource(R.string.wc_info_all, plan.keptFrames, plan.width, plan.height, plan.intervalMs)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showReal = true }, enabled = preview != null || previewLoop.isNotEmpty()) {
                            Text(stringResource(R.string.wc_real_size), maxLines = 1)
                        }
                        OutlinedButton(onClick = { copyOpen = true }) { Text(stringResource(R.string.wc_copy_look), maxLines = 1) }
                    }
                    copiedFrom?.let { Text(stringResource(R.string.wc_copy_look_done, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val reset = stringResource(R.string.wc_reset)

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_gif),
                    initiallyOpen = true,
                    summary = chosen?.name,
                ) {
                    Button(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")),
                            )
                        },
                        enabled = !adding && !saving,
                    ) { Text(stringResource(if (adding) R.string.wc_adding else R.string.wc_pick_gif)) }
                    if (adding) {
                        if (addProgress > 0f) {
                            LinearProgressIndicator(progress = { addProgress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    addMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (loaded && animations.none { it.isOptimized }) {
                        Text(stringResource(R.string.wc_no_animations))
                    }
                    // A grid of thumbnails with a search box: easy to scan even with dozens of GIFs.
                    GifGrid(
                        animations = animations,
                        isSelected = { it.id == settings.animationId },
                        onPick = { meta -> update { it.copy(animationId = meta.id) } },
                    )
                }

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_playback),
                    summary = "${settings.speedPercent} %",
                    resetLabel = reset,
                    onReset = { update { it.resetPlayback() } },
                ) {
                    LabeledSlider(stringResource(R.string.wc_speed, settings.speedPercent), settings.speedPercent, 25..300) { v -> update { it.copy(speedPercent = v) } }
                    if (chosen != null) {
                        SwitchLine(stringResource(R.string.wc_hq), settings.highQuality) { on ->
                            if (hqBusy) return@SwitchLine
                            if (!on) {
                                update { it.copy(highQuality = false) }
                            } else {
                                hqBusy = true
                                hqProgress = 0f
                                hqMessage = null
                                scope.launch {
                                    // Makes the full-speed, full-size frames once (needs the original GIF).
                                    val result = withContext(Dispatchers.Default) {
                                        HighQualityFrames.prepare(context.applicationContext, chosen, onProgress = { hqProgress = it })
                                    }
                                    hqBusy = false
                                    if (result == HighQualityFrames.Result.FAILED) {
                                        hqMessage = context.getString(R.string.wc_hq_failed)
                                    } else {
                                        if (result == HighQualityFrames.Result.NO_ORIGINAL) {
                                            hqMessage = context.getString(R.string.wc_hq_no_original)
                                        }
                                        update { it.copy(highQuality = true) }
                                        // Optional: without it the service still runs, only its notification stays hidden.
                                        if (Build.VERSION.SDK_INT >= 33 &&
                                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                            PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.wc_hq_hint), style = MaterialTheme.typography.bodySmall)
                        if (hqBusy) {
                            Text(stringResource(R.string.wc_hq_preparing), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { hqProgress }, modifier = Modifier.fillMaxWidth())
                        }
                        hqMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_framing),
                    resetLabel = reset,
                    onReset = { update { it.resetFraming() } },
                ) {
                    Text(stringResource(R.string.wc_scale))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        ScaleMode.entries.forEach { mode ->
                            GifChip(
                                selected = settings.scale == mode,
                                onClick = { update { it.copy(scale = mode) } },
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                ScaleMode.AUTO -> R.string.wc_scale_auto
                                                ScaleMode.FIT -> R.string.wc_scale_fit
                                                ScaleMode.FILL -> R.string.wc_scale_fill
                                                ScaleMode.CENTER -> R.string.wc_scale_center
                                            },
                                        ),
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    Text(stringResource(R.string.wc_scale_hint), style = MaterialTheme.typography.bodySmall)
                    LabeledSlider(stringResource(R.string.wc_zoom, settings.zoomPercent), settings.zoomPercent, 50..300) { v -> update { it.copy(zoomPercent = v) } }
                    LabeledSlider(stringResource(R.string.wc_padding, settings.paddingDp), settings.paddingDp, 0..24) { v -> update { it.copy(paddingDp = v) } }
                }

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_frame),
                    resetLabel = reset,
                    onReset = { update { it.resetFrame() } },
                ) {
                    LabeledSlider(stringResource(R.string.wc_corners, settings.cornerDp), settings.cornerDp, 0..48) { v -> update { it.copy(cornerDp = v) } }
                    LabeledSlider(stringResource(R.string.wc_border, settings.borderDp), settings.borderDp, 0..8) { v -> update { it.copy(borderDp = v) } }
                    if (settings.borderDp > 0) {
                        Text(stringResource(R.string.wc_border_color))
                        ColorRow(settings.borderColor) { c -> update { it.copy(borderColor = c) } }
                    }
                    LabeledSlider(stringResource(R.string.wc_bg_opacity, settings.bgOpacity), settings.bgOpacity, 0..100) { v -> update { it.copy(bgOpacity = v) } }
                    if (settings.bgOpacity > 0) {
                        Text(stringResource(R.string.wc_bg_color))
                        ColorRow(settings.bgColor) { c -> update { it.copy(bgColor = c) } }
                    }
                }

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_colors),
                    resetLabel = reset,
                    onReset = { update { it.resetColors() } },
                ) {
                    LabeledSlider(stringResource(R.string.wc_brightness, settings.brightness), settings.brightness, 50..150) { v -> update { it.copy(brightness = v) } }
                    LabeledSlider(stringResource(R.string.wc_opacity, settings.opacity), settings.opacity, 10..100) { v -> update { it.copy(opacity = v) } }
                    SwitchLine(stringResource(R.string.wc_sharp), settings.sharp) { v -> update { it.copy(sharp = v) } }
                }

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_rotation),
                    summary = if (settings.autoRotateMinutes > 0) intervalLabel(settings.autoRotateMinutes) else null,
                    resetLabel = reset,
                    onReset = { update { it.copy(rotation = emptyList(), autoRotateMinutes = 0) } },
                ) {
                    Text(stringResource(R.string.wc_rotation), style = MaterialTheme.typography.bodySmall)
                    GifGrid(
                        animations = animations.filter { it.isOptimized },
                        isSelected = { it.id == settings.animationId || it.id in settings.rotation },
                        onPick = { meta ->
                            // The shown animation always stays in the cycle.
                            if (meta.id != settings.animationId) {
                                update { cur ->
                                    cur.copy(
                                        rotation = if (meta.id in cur.rotation) cur.rotation - meta.id else (cur.rotation + meta.id).distinct(),
                                    )
                                }
                            }
                        },
                    )
                    Text(stringResource(R.string.wc_auto_rotate))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        AutoRotate.CHOICES_MIN.forEach { minutes ->
                            GifChip(
                                selected = settings.autoRotateMinutes == minutes,
                                onClick = { update { it.copy(autoRotateMinutes = minutes) } },
                                label = { Text(if (minutes == 0) stringResource(R.string.wc_auto_off) else intervalLabel(minutes), maxLines = 1) },
                            )
                        }
                    }
                    Text(
                        stringResource(if (settings.autoRotateMinutes > 0 && settings.rotation.isEmpty()) R.string.wc_auto_need else R.string.wc_auto_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                CollapsibleSection(
                    title = stringResource(R.string.wc_sec_button),
                    resetLabel = reset,
                    onReset = { update { it.resetButton().copy(tap = TapAction.TOGGLE) } },
                ) {
                    SwitchLine(stringResource(R.string.wc_show_button), settings.showButton) { v -> update { it.copy(showButton = v) } }
                    if (settings.showButton) {
                        Text(stringResource(R.string.wc_button_corner))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            ButtonCorner.entries.forEach { corner ->
                                GifChip(
                                    selected = settings.buttonCorner == corner,
                                    onClick = { update { it.copy(buttonCorner = corner) } },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (corner) {
                                                    ButtonCorner.TOP_START -> R.string.wc_corner_tl
                                                    ButtonCorner.TOP_END -> R.string.wc_corner_tr
                                                    ButtonCorner.BOTTOM_START -> R.string.wc_corner_bl
                                                    ButtonCorner.BOTTOM_END -> R.string.wc_corner_br
                                                },
                                            ),
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.wc_tap), style = MaterialTheme.typography.titleMedium)
                    TapAction.entries.filter { it != TapAction.PLAY_ONCE || playOnceEnabled || settings.tap == TapAction.PLAY_ONCE }.forEach { action ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { update { it.copy(tap = action) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GifRadio(selected = settings.tap == action, onClick = { update { it.copy(tap = action) } })
                            Text(
                                stringResource(
                                    when (action) {
                                        TapAction.TOGGLE -> R.string.wc_tap_toggle
                                        TapAction.TOGGLE_ALL -> R.string.wc_tap_toggle_all
                                        TapAction.NEXT_ANIMATION -> R.string.wc_tap_next
                                        TapAction.PLAY_ONCE -> R.string.wc_tap_play
                                        TapAction.OPEN_APP -> R.string.wc_tap_open
                                        TapAction.NONE -> R.string.wc_tap_none
                                    },
                                ),
                            )
                        }
                    }
                    if (settings.tap == TapAction.NEXT_ANIMATION) {
                        Text(stringResource(R.string.wc_tap_next_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.wc_cancel)) }
                    Button(
                        onClick = {
                            saving = true
                            onSave(withRotation(settings, initial)) { saving = false }
                        },
                        enabled = chosen != null && !saving && !adding,
                    ) { Text(stringResource(if (saving) R.string.wc_saving else R.string.wc_save)) }
                }
                Box(Modifier.height(24.dp))
            }
        }
    }
}

/** Another placed widget whose look can be copied. */
private class LookSource(val name: String, val settings: WidgetSettings)

private fun loadLookSources(app: Context, exceptId: Int): List<LookSource> {
    val manager = AppWidgetManager.getInstance(app)
    val store = WidgetStore(app)
    val library = LibraryStore(app)
    return WidgetUpdater.allIds(app, manager).sorted().filter { it != exceptId && store.hasSettings(it) }.map { id ->
        val s = store.loadSettings(id)
        val meta = s.animationId?.takeIf { AnimationMeta.isValidId(it) }?.let { library.read(it) }
        LookSource(meta?.name ?: app.getString(R.string.mw_widget_name, id), s)
    }
}

@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes >= 1440 -> stringResource(R.string.wc_every_day)
    minutes >= 60 -> stringResource(R.string.wc_every_hours, minutes / 60)
    else -> stringResource(R.string.wc_every_minutes, minutes)
}

/** The animations as a grid of thumbnails, with a search box when there are many. Tap one to pick it. */
@Composable
private fun GifGrid(
    animations: List<AnimationMeta>,
    isSelected: (AnimationMeta) -> Boolean,
    onPick: (AnimationMeta) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(animations, query) {
        val needle = query.trim()
        if (needle.isEmpty()) animations else animations.filter { it.name.contains(needle, ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (animations.size > 6) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = stringResource(R.string.search_hint),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.library_no_results), style = MaterialTheme.typography.bodyMedium)
        } else {
            val rows = (shown.size + 2) / 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(minOf(rows * 112, 336).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { meta ->
                    GifGridCell(meta, selected = isSelected(meta), onClick = { onPick(meta) })
                }
            }
        }
    }
}

@Composable
private fun GifGridCell(meta: AnimationMeta, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var thumb by remember(meta.id, meta.editedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(meta.id, meta.editedAt) {
        thumb = withContext(Dispatchers.IO) {
            ThumbnailStore.loadFirst(context.applicationContext, meta)?.asImageBitmap()
        }
    }
    Column(
        modifier = Modifier
            .alpha(if (meta.isOptimized) 1f else 0.4f)
            .clickable(enabled = meta.isOptimized, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(Color(0xFF1A0F3A))
                .border(if (selected) 4.dp else 2.dp, if (selected) scheme.primary else scheme.outline),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = meta.name,
                    modifier = Modifier.fillMaxSize().padding(if (selected) 4.dp else 2.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            }
        }
        Text(
            text = meta.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The list of animations to cycle always contains the shown animation first, so the cycle starts there.
 * When automatic rotation is on, its clock starts now (or again when the interval changed).
 */
private fun withRotation(s: WidgetSettings, before: WidgetSettings, nowMs: Long = System.currentTimeMillis()): WidgetSettings {
    val list = if (s.animationId != null && s.rotation.isNotEmpty()) (listOf(s.animationId) + s.rotation).distinct() else emptyList()
    val withList = s.copy(rotation = list)
    val restart = withList.autoRotates && (before.autoRotateMinutes != withList.autoRotateMinutes || withList.rotatedAtMs == 0L)
    return if (restart) withList.copy(rotatedAtMs = nowMs) else withList
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Text(label)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PALETTE.forEach { color ->
            val isSelected = (color or 0xFF000000.toInt()) == (selected or 0xFF000000.toInt())
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(color), CircleShape)
                    .border(
                        if (isSelected) 3.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .clickable { onSelect(color) },
            )
        }
    }
}
