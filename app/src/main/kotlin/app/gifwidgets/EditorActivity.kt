package app.gifwidgets

import app.gifwidgets.ui.design.ScreenWindow
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.gifwidgets.ui.design.GifDialog
import app.gifwidgets.ui.design.Button
import app.gifwidgets.ui.design.GifChip
import app.gifwidgets.ui.design.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import app.gifwidgets.ui.design.OutlinedButton
import androidx.compose.material3.Scaffold
import app.gifwidgets.ui.design.Slider
import androidx.compose.material3.Surface
import app.gifwidgets.ui.design.Switch
import app.gifwidgets.ui.design.GifTab
import app.gifwidgets.ui.design.GifTabRow
import androidx.compose.material3.Text
import app.gifwidgets.ui.design.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import app.gifwidgets.ui.design.KixkiCorner
import app.gifwidgets.control.ControlStore
import app.gifwidgets.edit.AutoFit
import app.gifwidgets.edit.BackgroundMode
import app.gifwidgets.edit.BackgroundParams
import app.gifwidgets.edit.CropRect
import app.gifwidgets.edit.EditorParams
import app.gifwidgets.edit.EditorSession
import app.gifwidgets.edit.FrameProcessor
import app.gifwidgets.edit.ImageMath
import app.gifwidgets.edit.ImageParams
import app.gifwidgets.edit.OptimizedWriter
import app.gifwidgets.edit.PreviewFrame
import app.gifwidgets.edit.SaveFailure
import app.gifwidgets.edit.SaveResult
import app.gifwidgets.edit.Timeline
import app.gifwidgets.edit.TimelineParams
import app.gifwidgets.gif.GifReader
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.memory.ScreenMetrics
import app.gifwidgets.memory.WidgetMemoryCalculator
import app.gifwidgets.ui.theme.AppTheme
import app.gifwidgets.widget.GifWidgetProvider
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor for one animation (stage 3a: background removal, frame rate, maximum size, WebP saving).
 * Not exported: only our own library screen starts it. It always works from original.gif.
 */
class EditorActivity : ComponentActivity() {
    /** True while the editor holds changes that were not saved with "Guardar optimizado". */
    private var dirty by mutableStateOf(false)
    private var askDiscard by mutableStateOf(false)
    private lateinit var backCallback: OnBackPressedCallback

    /** Every way out (title bar, "Atrás" button, system back) asks first when there is something to lose. */
    private fun requestClose() {
        if (dirty) askDiscard = true else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null || !AnimationMeta.isValidId(id)) {
            finish()
            return
        }
        // Disabled while clean, so the normal back gesture (and its animation) is untouched.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                askDiscard = true
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenWindow(
                        title = stringResource(R.string.win_editor),
                        onClose = { requestClose() },
                        actions = { KixkiCorner(stringArrayResource(R.array.tips_editor).toList()) },
                    ) {
                        EditorScreen(
                            id = id,
                            onClose = { requestClose() },
                            onDirtyChange = { isDirty ->
                                dirty = isDirty
                                backCallback.isEnabled = isDirty
                            },
                        )
                    }
                    if (askDiscard) {
                        GifDialog(
                            onDismissRequest = { askDiscard = false },
                            title = { Text(stringResource(R.string.editor_discard_title)) },
                            text = { Text(stringResource(R.string.editor_discard_body)) },
                            confirmButton = {
                                TextButton(onClick = { askDiscard = false; finish() }) {
                                    Text(stringResource(R.string.editor_discard_action))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { askDiscard = false }) {
                                    Text(stringResource(R.string.editor_discard_keep))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "animation_id"
        fun intent(context: Context, id: String): Intent =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_ID, id)
    }
}

private enum class Backdrop { CHECKER, WHITE, BLACK, COLOR }

private val MAX_SIDE_CHOICES = listOf(0, 720, 540, 360, 240)
private val TAB_TITLES = listOf(
    R.string.editor_tab_background,
    R.string.editor_tab_time,
    R.string.editor_tab_image,
    R.string.editor_tab_memory,
)

@Composable
private fun EditorScreen(id: String, onClose: () -> Unit, onDirtyChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }

    var meta by remember { mutableStateOf<AnimationMeta?>(null) }
    var session by remember { mutableStateOf<EditorSession?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var params by remember { mutableStateOf(EditorParams()) }
    /** The parameters as last loaded or saved: anything different is an unsaved change. */
    var baseline by remember { mutableStateOf<EditorParams?>(null) }
    val dirty = baseline?.let { it != params } ?: false
    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    var frameIndex by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf<PreviewFrame?>(null) }
    var originalBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var autoBounds by remember { mutableStateOf<CropRect?>(null) } // in preview pixels
    var showOriginal by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf(Backdrop.CHECKER) }
    var eyedropper by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    // Simple by default: background, speed and size. "Avanzado" brings back the four tabs.
    var advanced by rememberSaveable { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    var savedDialog by remember { mutableStateOf<SaveResult.Success?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    /** "Conservar originales" is off: original.gif was deleted after saving, so the editor closes. */
    var originalDeleted by remember { mutableStateOf(false) }

    // Load the animation and open its original.gif.
    LaunchedEffect(id) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                val m = store.read(id) ?: return@withContext null
                val file = File(store.dirFor(id), LibraryStore.ORIGINAL_FILE)
                m to EditorSession(GifReader.open(file.readBytes()))
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
        if (loaded == null) {
            loadFailed = true
        } else {
            meta = loaded.first
            session = loaded.second
            val defaults = withContext(Dispatchers.IO) { ControlStore(context).load() }
            params = loaded.first.params ?: EditorParams(
                timeline = TimelineParams(fps = loaded.first.fps.coerceIn(5, defaults.defaultFps)),
                image = ImageParams(sharpPixels = defaults.sharpPixels),
            )
            baseline = params
        }
    }

    // Decode the frame shown in the preview.
    LaunchedEffect(session, frameIndex) {
        val s = session ?: return@LaunchedEffect
        // isActive: when the slider moves on, the decode of the old position stops instead of piling up.
        val frame = withContext(Dispatchers.Default) { s.previewFrame(frameIndex, isCancelled = { !isActive }) }
        source = frame
        originalBitmap = withContext(Dispatchers.Default) {
            Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    // Apply the background settings to the preview frame.
    LaunchedEffect(source, params.background) {
        val frame = source ?: return@LaunchedEffect
        val bg = params.background
        val result = withContext(Dispatchers.Default) {
            val px = FrameProcessor.withBackground(frame.pixels, frame.width, frame.height, bg)
            val bmp = Bitmap.createBitmap(px, frame.width, frame.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            bmp to ImageMath.opaqueBounds(px, frame.width, frame.height)
        }
        processedBitmap = result.first
        autoBounds = result.second
    }

    fun updateBg(change: (BackgroundParams) -> BackgroundParams) {
        params = params.copy(background = change(params.background))
    }

    fun startSave() {
        val m = meta ?: return
        val toSave = params
        saving = true
        progress = 0f
        saveJob = scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    OptimizedWriter.save(
                        context.applicationContext, m, params,
                        onProgress = { done, total -> progress = if (total > 0) done.toFloat() / total else 0f },
                        isCancelled = { !isActive },
                    )
                }
                when (result) {
                    is SaveResult.Success -> {
                        meta = result.meta
                        baseline = toSave // saved: nothing to lose any more
                        savedDialog = result
                        originalDeleted = withContext(Dispatchers.IO) {
                            !ControlStore(context).load().keepOriginals && store.deleteOriginal(id)
                        }
                        GifWidgetProvider.refreshAll(context) // widgets using this animation pick up the new frames
                    }
                    is SaveResult.Failure -> errorText = context.getString(
                        when (result.reason) {
                            SaveFailure.CANNOT_READ -> R.string.editor_error_read
                            SaveFailure.CORRUPT -> R.string.editor_error_corrupt
                            SaveFailure.OUT_OF_MEMORY -> R.string.editor_error_memory
                            SaveFailure.ENCODE_FAILED -> R.string.editor_error_encode
                        },
                    )
                    SaveResult.Cancelled -> Unit
                }
            } finally {
                saving = false
            }
        }
    }

    savedDialog?.let { done ->
        GifDialog(
            onDismissRequest = { savedDialog = null; if (originalDeleted) onClose() },
            title = { Text(stringResource(R.string.editor_saved_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.editor_saved_body,
                        Formatter.formatShortFileSize(context, done.beforeBytes),
                        Formatter.formatShortFileSize(context, done.afterBytes),
                        done.meta.outFrames,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { savedDialog = null; if (originalDeleted) onClose() }) {
                    Text(stringResource(R.string.editor_ok))
                }
            },
        )
    }
    errorText?.let { text ->
        GifDialog(
            onDismissRequest = { errorText = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.editor_ok)) } },
        )
    }

    Scaffold { innerPadding ->
        val s = session
        val m = meta
        if (loadFailed) {
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.editor_load_error))
                Button(onClick = onClose) { Text(stringResource(R.string.editor_back)) }
            }
        } else if (s == null || m == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.editor_loading))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.editor_title, m.name), style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose, enabled = !saving) { Text(stringResource(R.string.editor_back)) }
                }

                val src = source
                val cropOverlay: CropRect? = when {
                    tab != 2 || src == null -> null
                    params.image.autoCrop -> autoBounds
                    else -> params.image.manualCrop?.let { c ->
                        val k = src.width / s.width.toFloat()
                        CropRect((c.left * k).roundToInt(), (c.top * k).roundToInt(), (c.right * k).roundToInt(), (c.bottom * k).roundToInt())
                    }
                }
                PreviewBox(
                    shown = if (showOriginal) originalBitmap else processedBitmap,
                    source = source,
                    overlay = cropOverlay,
                    backdrop = backdrop,
                    eyedropper = eyedropper,
                    onPick = { color ->
                        eyedropper = false
                        updateBg { it.copy(mode = BackgroundMode.COLOR_KEY, keyColor = color or 0xFF000000.toInt()) }
                    },
                )

                if (s.frameCount > 1) {
                    Text(stringResource(R.string.editor_frame, frameIndex + 1, s.frameCount), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = frameIndex.toFloat(),
                        onValueChange = { frameIndex = it.roundToInt().coerceIn(0, s.frameCount - 1) },
                        valueRange = 0f..(s.frameCount - 1).toFloat(),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackdropChip(Backdrop.CHECKER, backdrop, R.string.editor_backdrop_checker) { backdrop = it }
                    BackdropChip(Backdrop.WHITE, backdrop, R.string.editor_backdrop_white) { backdrop = it }
                    BackdropChip(Backdrop.BLACK, backdrop, R.string.editor_backdrop_black) { backdrop = it }
                    BackdropChip(Backdrop.COLOR, backdrop, R.string.editor_backdrop_color) { backdrop = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = showOriginal, onCheckedChange = { showOriginal = it })
                    Text(stringResource(R.string.editor_compare))
                }

                // ---- Shared numbers for the tabs ----
                val bg = params.background
                val plan = remember(s, params.timeline) { Timeline.plan(s.delaysCs, params.timeline) }
                val (outW, outH) = AutoFit.frameSize(s.width, s.height, params.image)
                val screen = remember { ScreenMetrics.physicalSizePx(context) }
                val maxBytes = WidgetMemoryCalculator.maxBudgetBytes(screen.width, screen.height)
                val sourceHasAlpha = s.usesTransparency || bg.mode != BackgroundMode.KEEP
                val bpp = AutoFit.bytesPerPixel(params, s.usesTransparency)
                val usedBytes = plan.frameCount.toLong() * outW * outH * bpp
                val overBudget = usedBytes > maxBytes

                fun updateTimeline(change: (TimelineParams) -> TimelineParams) {
                    params = params.copy(timeline = change(params.timeline))
                }
                fun updateImage(change: (ImageParams) -> ImageParams) {
                    params = params.copy(image = change(params.image))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GifChip(
                        selected = advanced,
                        onClick = { advanced = !advanced },
                        label = { Text(stringResource(R.string.editor_advanced)) },
                    )
                    if (!advanced) {
                        Text(stringResource(R.string.editor_simple_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                if (advanced) {
                    GifTabRow(selectedTabIndex = tab) {
                        TAB_TITLES.forEachIndexed { i, res ->
                            GifTab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res), maxLines = 1) })
                        }
                    }
                } else {
                    Text(stringResource(R.string.editor_tab_background), style = MaterialTheme.typography.titleMedium)
                }

                if (!advanced || tab == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GifChip(
                            selected = bg.mode == BackgroundMode.KEEP,
                            onClick = { updateBg { it.copy(mode = BackgroundMode.KEEP) } },
                            label = { Text(stringResource(R.string.editor_bg_keep)) },
                        )
                        GifChip(
                            selected = bg.mode == BackgroundMode.COLOR_KEY,
                            onClick = { updateBg { it.copy(mode = BackgroundMode.COLOR_KEY) } },
                            label = { Text(stringResource(R.string.editor_bg_key)) },
                        )
                    }
                    GifChip(
                        selected = bg.mode == BackgroundMode.BLACK_TO_ALPHA,
                        onClick = { updateBg { it.copy(mode = BackgroundMode.BLACK_TO_ALPHA) } },
                        label = { Text(stringResource(R.string.editor_bg_black)) },
                    )

                    when (bg.mode) {
                        BackgroundMode.KEEP -> Text(stringResource(R.string.editor_bg_keep_help), style = MaterialTheme.typography.bodySmall)

                        BackgroundMode.COLOR_KEY -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(Color(bg.keyColor or 0xFF000000.toInt()), RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                )
                                if (eyedropper) {
                                    Button(onClick = { eyedropper = false }) { Text(stringResource(R.string.editor_eyedropper)) }
                                } else {
                                    OutlinedButton(onClick = { eyedropper = true; showOriginal = true }) {
                                        Text(stringResource(R.string.editor_eyedropper))
                                    }
                                }
                            }
                            if (eyedropper) {
                                Text(stringResource(R.string.editor_eyedropper_help), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(stringResource(R.string.editor_tolerance, bg.tolerance))
                            Slider(
                                value = bg.tolerance.toFloat(),
                                onValueChange = { v -> updateBg { it.copy(tolerance = v.roundToInt()) } },
                                valueRange = 0f..100f,
                            )
                            SwitchRow(stringResource(R.string.editor_contiguous), bg.contiguous) { v -> updateBg { it.copy(contiguous = v) } }
                            Text(stringResource(R.string.editor_soften, bg.edgeSoften))
                            Slider(
                                value = bg.edgeSoften.toFloat(),
                                onValueChange = { v -> updateBg { it.copy(edgeSoften = v.roundToInt()) } },
                                valueRange = 0f..3f,
                                steps = 2,
                            )
                            SwitchRow(stringResource(R.string.editor_defringe), bg.defringe) { v -> updateBg { it.copy(defringe = v) } }
                        }

                        BackgroundMode.BLACK_TO_ALPHA -> {
                            Text(stringResource(R.string.editor_black_threshold, bg.blackThreshold))
                            Slider(
                                value = bg.blackThreshold.toFloat(),
                                onValueChange = { v -> updateBg { it.copy(blackThreshold = v.roundToInt()) } },
                                valueRange = 0f..120f,
                            )
                            Text(stringResource(R.string.editor_black_gain, bg.blackGain))
                            Slider(
                                value = bg.blackGain.toFloat(),
                                onValueChange = { v -> updateBg { it.copy(blackGain = v.roundToInt()) } },
                                valueRange = 50f..400f,
                            )
                            Text(stringResource(R.string.editor_black_help), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (advanced && tab == 1) {
                    val t = params.timeline
                    val n = s.frameCount
                    val trimEnd = if (t.trimEnd < 0) n - 1 else t.trimEnd.coerceIn(0, n - 1)
                    val used = frameIndex in t.trimStart..trimEnd && frameIndex !in t.deleted
                    if (!used) {
                        Text(stringResource(R.string.editor_frame_unused), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (n > 1) {
                        Text(stringResource(R.string.editor_trim_start, t.trimStart + 1))
                        Slider(
                            value = t.trimStart.toFloat(),
                            onValueChange = { v ->
                                val st = v.roundToInt().coerceIn(0, n - 1)
                                val en = maxOf(trimEnd, st)
                                updateTimeline { it.copy(trimStart = st, trimEnd = if (en == n - 1) -1 else en) }
                            },
                            valueRange = 0f..(n - 1).toFloat(),
                        )
                        Text(stringResource(R.string.editor_trim_end, trimEnd + 1))
                        Slider(
                            value = trimEnd.toFloat(),
                            onValueChange = { v ->
                                val en = maxOf(v.roundToInt().coerceIn(0, n - 1), t.trimStart)
                                updateTimeline { it.copy(trimEnd = if (en == n - 1) -1 else en) }
                            },
                            valueRange = 0f..(n - 1).toFloat(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (frameIndex in t.deleted) {
                            OutlinedButton(onClick = { updateTimeline { it.copy(deleted = it.deleted - frameIndex) } }) {
                                Text(stringResource(R.string.editor_restore_frame))
                            }
                        } else {
                            OutlinedButton(onClick = { updateTimeline { it.copy(deleted = it.deleted + frameIndex) } }) {
                                Text(stringResource(R.string.editor_delete_frame))
                            }
                        }
                        if (t.deleted.isNotEmpty()) {
                            TextButton(onClick = { updateTimeline { it.copy(deleted = emptySet()) } }) {
                                Text(stringResource(R.string.editor_restore_all, t.deleted.size))
                            }
                        }
                    }
                    SwitchRow(stringResource(R.string.editor_reverse), t.reverse) { v -> updateTimeline { it.copy(reverse = v) } }
                    SwitchRow(stringResource(R.string.editor_pingpong), t.pingPong) { v -> updateTimeline { it.copy(pingPong = v) } }
                    Text(stringResource(R.string.editor_keep_one_of, t.keepOneOfN))
                    Slider(
                        value = t.keepOneOfN.toFloat(),
                        onValueChange = { v -> updateTimeline { it.copy(keepOneOfN = v.roundToInt().coerceIn(1, 10)) } },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                    Text(stringResource(R.string.editor_fps, plan.fps))
                    Slider(
                        value = plan.fps.toFloat(),
                        onValueChange = { v -> updateTimeline { it.copy(fps = v.roundToInt().coerceIn(1, 25)) } },
                        valueRange = 1f..25f,
                        steps = 23,
                    )
                    Text(
                        stringResource(R.string.editor_duration_info, plan.frameCount, "%.1f".format(plan.durationMs / 1000.0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(R.string.editor_rest_frame, plan.restFrame + 1, plan.frameCount))
                    if (plan.frameCount > 1) {
                        Slider(
                            value = plan.restFrame.toFloat(),
                            onValueChange = { v -> updateTimeline { it.copy(restFrame = v.roundToInt().coerceIn(0, plan.frameCount - 1)) } },
                            valueRange = 0f..(plan.frameCount - 1).toFloat(),
                        )
                    }
                    OutlinedButton(onClick = {
                        val k = Timeline.outputIndexFor(plan, frameIndex)
                        updateTimeline { it.copy(restFrame = k) }
                    }) { Text(stringResource(R.string.editor_rest_use_current)) }
                }

                if (advanced && tab == 2) {
                    val img = params.image
                    SwitchRow(stringResource(R.string.editor_autocrop), img.autoCrop) { v -> updateImage { it.copy(autoCrop = v) } }
                    if (img.autoCrop) {
                        Text(stringResource(R.string.editor_autocrop_note), style = MaterialTheme.typography.bodySmall)
                    } else {
                        val crop = img.manualCrop
                        val left = crop?.left ?: 0
                        val top = crop?.top ?: 0
                        val right = s.width - (crop?.right ?: s.width)
                        val bottom = s.height - (crop?.bottom ?: s.height)
                        fun setMargins(l: Int, t: Int, r: Int, b: Int) {
                            val rl = l.coerceIn(0, s.width - 1)
                            val rt = t.coerceIn(0, s.height - 1)
                            val rr = r.coerceIn(0, s.width - 1 - rl)
                            val rb = b.coerceIn(0, s.height - 1 - rt)
                            updateImage {
                                it.copy(
                                    manualCrop = if (rl == 0 && rt == 0 && rr == 0 && rb == 0) {
                                        null
                                    } else {
                                        CropRect(rl, rt, s.width - rr, s.height - rb)
                                    },
                                )
                            }
                        }
                        val maxX = maxOf(1, s.width / 2).toFloat()
                        val maxY = maxOf(1, s.height / 2).toFloat()
                        Text(stringResource(R.string.editor_crop_left, left))
                        Slider(value = left.toFloat(), onValueChange = { setMargins(it.roundToInt(), top, right, bottom) }, valueRange = 0f..maxX)
                        Text(stringResource(R.string.editor_crop_top, top))
                        Slider(value = top.toFloat(), onValueChange = { setMargins(left, it.roundToInt(), right, bottom) }, valueRange = 0f..maxY)
                        Text(stringResource(R.string.editor_crop_right, right))
                        Slider(value = right.toFloat(), onValueChange = { setMargins(left, top, it.roundToInt(), bottom) }, valueRange = 0f..maxX)
                        Text(stringResource(R.string.editor_crop_bottom, bottom))
                        Slider(value = bottom.toFloat(), onValueChange = { setMargins(left, top, right, it.roundToInt()) }, valueRange = 0f..maxY)
                    }
                    Text(stringResource(R.string.editor_max_side))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MAX_SIDE_CHOICES.forEach { side ->
                            GifChip(
                                selected = img.maxSide == side,
                                onClick = { updateImage { it.copy(maxSide = side) } },
                                label = { Text(if (side == 0) stringResource(R.string.editor_max_side_original) else side.toString()) },
                            )
                        }
                    }
                    SwitchRow(stringResource(R.string.editor_sharp), img.sharpPixels) { v -> updateImage { it.copy(sharpPixels = v) } }
                    Text(
                        stringResource(R.string.editor_output_info, plan.frameCount, outW, outH, "%.1f".format(plan.durationMs / 1000.0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (advanced && tab == 3) {
                    LinearProgressIndicator(
                        progress = { (usedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(
                            R.string.editor_memory,
                            Formatter.formatShortFileSize(context, usedBytes),
                            Formatter.formatShortFileSize(context, maxBytes),
                        ),
                        color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.editor_memory_detail, plan.frameCount, outW, outH, bpp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (overBudget) {
                        Text(stringResource(R.string.editor_memory_over), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { params = AutoFit.fit(params, s.delaysCs, s.width, s.height, bpp, maxBytes) },
                        enabled = overBudget,
                    ) { Text(stringResource(R.string.editor_autofit)) }
                    val memAllowed = !sourceHasAlpha
                    SwitchRow(
                        label = stringResource(if (memAllowed) R.string.editor_save_memory else R.string.editor_save_memory_unavailable),
                        checked = params.saveMemory && memAllowed,
                        enabled = memAllowed,
                    ) { v -> params = params.copy(saveMemory = v) }
                    Text(stringResource(R.string.editor_memory_note), style = MaterialTheme.typography.bodySmall)
                }

                // ---- Simple mode: speed and size, the two things almost everyone changes ----
                if (!advanced) {
                    Text(stringResource(R.string.editor_simple_speed_size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.editor_fps, plan.fps))
                    Slider(
                        value = plan.fps.toFloat(),
                        onValueChange = { v -> updateTimeline { it.copy(fps = v.roundToInt().coerceIn(1, 25)) } },
                        valueRange = 1f..25f,
                        steps = 23,
                    )
                    Text(stringResource(R.string.editor_max_side))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        MAX_SIDE_CHOICES.forEach { side ->
                            GifChip(
                                selected = params.image.maxSide == side,
                                onClick = { updateImage { it.copy(maxSide = side) } },
                                label = { Text(if (side == 0) stringResource(R.string.editor_max_side_original) else side.toString()) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.editor_output_info, plan.frameCount, outW, outH, "%.1f".format(plan.durationMs / 1000.0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (overBudget) {
                        Text(stringResource(R.string.editor_memory_over), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { params = AutoFit.fit(params, s.delaysCs, s.width, s.height, bpp, maxBytes) }) {
                            Text(stringResource(R.string.editor_autofit))
                        }
                    }
                }

                // ---- Saving (always visible) ----
                Text(stringResource(R.string.editor_save_title), style = MaterialTheme.typography.titleMedium)
                if (overBudget) {
                    Text(stringResource(R.string.editor_memory_over), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                val lossyAllowed = bg.mode == BackgroundMode.KEEP && !s.usesTransparency
                SwitchRow(
                    label = stringResource(if (lossyAllowed) R.string.editor_lossy else R.string.editor_lossy_unavailable),
                    checked = !params.lossless && lossyAllowed,
                    enabled = lossyAllowed,
                ) { v -> params = params.copy(lossless = !v) }
                if (!params.lossless && lossyAllowed) {
                    Text(stringResource(R.string.editor_quality, params.quality))
                    Slider(
                        value = params.quality.toFloat(),
                        onValueChange = { v -> params = params.copy(quality = v.roundToInt()) },
                        valueRange = 80f..95f,
                        steps = 14,
                    )
                }

                if (saving) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.editor_saving), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { saveJob?.cancel() }) { Text(stringResource(R.string.editor_cancel)) }
                } else {
                    Button(onClick = { startSave() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.editor_save_button))
                    }
                }
                Box(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BackdropChip(value: Backdrop, current: Backdrop, labelRes: Int, onSelect: (Backdrop) -> Unit) {
    GifChip(
        selected = value == current,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) },
    )
}

/** The preview: backdrop, the frame, and (in eyedropper mode) a tap that picks a source colour. */
@Composable
private fun PreviewBox(
    shown: ImageBitmap?,
    source: PreviewFrame?,
    overlay: CropRect?,
    backdrop: Backdrop,
    eyedropper: Boolean,
    onPick: (Int) -> Unit,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .onSizeChanged { box = it }
            .pointerInput(source, eyedropper, box) {
                if (eyedropper && source != null) {
                    detectTapGestures { tap ->
                        val scale = minOf(box.width / source.width.toFloat(), box.height / source.height.toFloat())
                        val offX = (box.width - source.width * scale) / 2f
                        val offY = (box.height - source.height * scale) / 2f
                        if (tap.x >= offX && tap.y >= offY) {
                            val x = ((tap.x - offX) / scale).toInt()
                            val y = ((tap.y - offY) / scale).toInt()
                            if (x in 0 until source.width && y in 0 until source.height) {
                                val c = source.pixels[y * source.width + x]
                                if ((c ushr 24) != 0) onPick(c)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (backdrop) {
            Backdrop.CHECKER -> Canvas(Modifier.fillMaxSize()) {
                val tile = 12.dp.toPx()
                val cols = (size.width / tile).toInt() + 1
                val rows = (size.height / tile).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        drawRect(
                            color = if ((r + c) % 2 == 0) Color(0xFFCCCCCC) else Color(0xFF999999),
                            topLeft = Offset(c * tile, r * tile),
                            size = Size(tile, tile),
                        )
                    }
                }
            }
            Backdrop.WHITE -> Box(Modifier.fillMaxSize().background(Color.White))
            Backdrop.BLACK -> Box(Modifier.fillMaxSize().background(Color.Black))
            Backdrop.COLOR -> Box(Modifier.fillMaxSize().background(Color(0xFFC83CA0)))
        }
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        }
        if (overlay != null && source != null) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = minOf(size.width / source.width, size.height / source.height)
                val offX = (size.width - source.width * scale) / 2f
                val offY = (size.height - source.height * scale) / 2f
                drawRect(
                    color = Color(0xFFFF3B30),
                    topLeft = Offset(offX + overlay.left * scale, offY + overlay.top * scale),
                    size = Size(overlay.width * scale, overlay.height * scale),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
