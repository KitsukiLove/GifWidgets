package app.gifwidgets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import app.gifwidgets.ui.design.GifDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.gifwidgets.ui.design.hardShadow
import app.gifwidgets.ui.design.PixelIcon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import app.gifwidgets.ui.design.SceneBackdrop
import app.gifwidgets.control.SeasonChoice
import app.gifwidgets.control.SeasonLogic
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.repeatOnLifecycle
import app.gifwidgets.control.ControlActions
import app.gifwidgets.control.ControlState
import app.gifwidgets.control.GlobalPause
import app.gifwidgets.control.GlobalPauseLogic
import app.gifwidgets.control.ControlStore
import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.NightAlarm
import app.gifwidgets.control.ThemeMode
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.AlphaCheck
import app.gifwidgets.library.AnimationNames
import app.gifwidgets.library.AnimationSort
import app.gifwidgets.library.GifQuickAdd
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.library.SortOrder
import app.gifwidgets.library.ThumbnailStore
import app.gifwidgets.memory.ScreenMetrics
import app.gifwidgets.memory.WidgetMemoryCalculator
import app.gifwidgets.ui.design.AddTile
import app.gifwidgets.ui.design.NameField
import app.gifwidgets.ui.PendingGif
import app.gifwidgets.ui.AddNameDialog
import app.gifwidgets.library.GifImporter
import app.gifwidgets.library.LibraryOrganizer
import app.gifwidgets.backup.BackupNudge
import app.gifwidgets.control.AppSettings
import app.gifwidgets.ui.design.GifChip
import app.gifwidgets.widget.RotationAlarm
import app.gifwidgets.ui.design.BarIconButton
import app.gifwidgets.ui.design.BarItem
import app.gifwidgets.ui.design.Button
import app.gifwidgets.ui.design.Card
import app.gifwidgets.ui.design.GifBottomBar
import app.gifwidgets.ui.design.PillChip
import app.gifwidgets.ui.design.PixelIcons
import app.gifwidgets.ui.design.SearchField
import app.gifwidgets.ui.design.Tag
import app.gifwidgets.ui.design.TextButton
import app.gifwidgets.ui.design.KixkiCorner
import app.gifwidgets.ui.design.KixkiWalker
import app.gifwidgets.ui.design.OutlinedButton
import app.gifwidgets.ui.design.ToolButton
import app.gifwidgets.ui.design.Wordmark
import app.gifwidgets.ui.design.KixkiWelcome
import app.gifwidgets.ui.design.cardTint
import app.gifwidgets.ui.design.WindowCard
import app.gifwidgets.ui.design.fullBleed
import app.gifwidgets.ui.messageRes
import app.gifwidgets.ui.theme.AppTheme
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.ui.theme.LocalThemeController
import app.gifwidgets.widget.GifWidgetProvider
import app.gifwidgets.widget.WidgetPinner
import app.gifwidgets.widget.WidgetPreviews
import app.gifwidgets.widget.WidgetUpdater
import app.gifwidgets.util.runGuarded
import app.gifwidgets.util.runGuardedResult
import java.text.NumberFormat
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage 2: library grid with animated thumbnails, import, rename, duplicate, delete and sort.
 * Thumbnails only move while this screen is visible (see [AnimatedThumb]).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen(lifecycle)
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(lifecycle: Lifecycle) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val store = remember { LibraryStore(context) }

    var library by remember { mutableStateOf<List<AnimationMeta>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var importCurrent by remember { mutableIntStateOf(1) }
    var importTotal by remember { mutableIntStateOf(1) }
    var renaming by remember { mutableStateOf<AnimationMeta?>(null) }
    var deleting by remember { mutableStateOf<AnimationMeta?>(null) }
    var pinning by remember { mutableStateOf<AnimationMeta?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.NEWEST) }
    var masterOn by remember { mutableStateOf(true) }
    var pause by remember { mutableStateOf(GlobalPause.NONE) }
    var usedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // null = normal; a set = "Seleccionar" mode with those GIF chosen.
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var deletingMany by remember { mutableStateOf<List<AnimationMeta>?>(null) }
    var collecting by remember { mutableStateOf<List<AnimationMeta>?>(null) }
    var compact by remember { mutableStateOf(false) }
    var appSnap by remember { mutableStateOf(AppSettings()) }

    suspend fun refresh() {
        library = withContext(Dispatchers.IO) { store.list() }
        usedIds = withContext(Dispatchers.IO) {
            try {
                WidgetUpdater.usedAnimationIds(context.applicationContext)
            } catch (e: RuntimeException) {
                emptySet()
            }
        }
    }

    // Why the widgets are still (if they are): the message at the top of the library.
    suspend fun loadGlobal() {
        withContext(Dispatchers.IO) {
            val app = ControlStore(context).load()
            masterOn = app.masterEnabled
            compact = app.compactList
            appSnap = app
            pause = GlobalPauseLogic.reason(
                app.masterEnabled, app.pauseOnBatterySaver, ControlState.powerSave(context), app.night.isNight(LocalDateTime.now()),
            )
        }
    }

    // Reload every time the screen comes back (for example after saving in the editor).
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            refresh()
            // Opening the app re-evaluates the global state (night, battery saver) and re-arms the night alarm.
            withContext(Dispatchers.IO) { NightAlarm.arm(context); runGuarded { RotationAlarm.arm(context) } }
            loadGlobal()
            GifWidgetProvider.refreshAll(context)
            // Old entries: replace the GIF's "transparent colour" flag by the real answer (once per entry).
            launch {
                val changed = withContext(Dispatchers.IO) { runGuardedResult { AlphaCheck.verifyAll(context.applicationContext) } }
                if (changed == true) refresh()
            }
            // Widget-picker preview (Android 15+): only calls the system when the newest animation changed.
            launch(Dispatchers.IO) { WidgetPreviews.refresh(context.applicationContext) }
            // Housekeeping: folders of widgets that were removed while an update was running.
            launch(Dispatchers.IO) { runGuarded { WidgetUpdater.cleanOrphans(context.applicationContext) } }
        }
    }

    // The "name this GIF" question comes first: every picked GIF is asked about (the suggestion comes from its file
    // name), then they are imported one after the other (never in parallel: memory) and each one appears in the
    // library as soon as it is ready.
    var askQueue by remember { mutableStateOf<List<PendingGif>>(emptyList()) }
    var named by remember { mutableStateOf<List<PendingGif>>(emptyList()) }

    fun runImport(items: List<PendingGif>) {
        if (items.isEmpty() || importing) return
        importing = true
        importTotal = items.size
        importCurrent = 1
        scope.launch {
            val fallbackName = context.getString(R.string.default_animation_name)
            var imported = 0
            var lastName = ""
            var failure: String? = null
            for ((n, item) in items.withIndex()) {
                importCurrent = n + 1
                // Import and save optimized in one go: the animation is ready for a widget right away,
                // and can still be re-edited from the original later.
                val result = withContext(Dispatchers.Default) {
                    runGuardedResult {
                        GifQuickAdd.add(context.applicationContext, item.uri, fallbackName, nameOverride = item.name)
                    }
                }
                when (result) {
                    is GifQuickAdd.Result.Success -> {
                        imported++
                        lastName = result.meta.name
                        refresh()
                    }
                    is GifQuickAdd.Result.ImportFailed -> failure = context.getString(result.reason.messageRes())
                    is GifQuickAdd.Result.SaveFailed -> failure = context.getString(result.reason.messageRes())
                    null -> failure = context.getString(R.string.import_batch_error)
                }
            }
            importing = false
            if (imported > 0) GifWidgetProvider.refreshAll(context) // empty widgets pick up their first animation
            val message = when {
                imported == 0 -> failure.orEmpty()
                items.size == 1 -> context.getString(R.string.import_done, lastName)
                imported == items.size -> context.getString(R.string.import_batch_done, imported)
                else -> context.getString(R.string.import_batch_partial, imported, items.size)
            }
            snackbar.showSnackbar(message)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
        if (uris.isNotEmpty() && !importing && askQueue.isEmpty()) {
            scope.launch {
                val fallbackName = context.getString(R.string.default_animation_name)
                val items = withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        PendingGif(uri, AnimationNames.suggest(GifImporter.displayName(context.applicationContext, uri), fallbackName))
                    }
                }
                named = emptyList()
                askQueue = items
            }
        }
    }

    askQueue.firstOrNull()?.let { current ->
        AddNameDialog(
            current = current,
            position = named.size + 1,
            total = named.size + askQueue.size,
            onConfirm = { name ->
                val done = named + current.copy(name = name)
                val rest = askQueue.drop(1)
                if (rest.isEmpty()) {
                    askQueue = emptyList()
                    named = emptyList()
                    runImport(done)
                } else {
                    named = done
                    askQueue = rest
                }
            },
            onConfirmAll = { name ->
                val all = named + current.copy(name = name) + askQueue.drop(1)
                askQueue = emptyList()
                named = emptyList()
                runImport(all)
            },
            onCancel = {
                askQueue = emptyList()
                named = emptyList()
            },
        )
    }

    renaming?.let { target ->
        RenameDialog(
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                renaming = null
                scope.launch {
                    withContext(Dispatchers.IO) { store.rename(target.id, newName) }
                    refresh()
                }
            },
        )
    }

    deleting?.let { target ->
        // How many placed widgets use it, to warn before they are left empty.
        var inUse by remember(target.id) { mutableIntStateOf(0) }
        LaunchedEffect(target.id) {
            inUse = withContext(Dispatchers.IO) {
                try {
                    WidgetUpdater.countUsing(context.applicationContext, target.id)
                } catch (e: RuntimeException) {
                    0
                }
            }
        }
        GifDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title, target.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_body))
                    if (inUse > 0) Text(stringResource(R.string.delete_in_use, inUse))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            withContext(Dispatchers.IO) { store.delete(target.id) }
                            GifWidgetProvider.refreshAll(context)
                            refresh()
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.rename_cancel)) }
            },
        )
    }

    deletingMany?.let { targets ->
        val inUseCount = targets.count { it.id in usedIds }
        GifDialog(
            onDismissRequest = { deletingMany = null },
            title = { Text(stringResource(R.string.delete_many_title, targets.size)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_body))
                    if (inUseCount > 0) Text(stringResource(R.string.delete_many_in_use, inUseCount))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingMany = null
                        selection = null
                        scope.launch {
                            withContext(Dispatchers.IO) { targets.forEach { store.delete(it.id) } }
                            GifWidgetProvider.refreshAll(context)
                            refresh()
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMany = null }) { Text(stringResource(R.string.rename_cancel)) }
            },
        )
    }

    pinning?.let { target ->
        GifDialog(
            onDismissRequest = { pinning = null },
            title = { Text(stringResource(R.string.pin_size_title)) },
            text = {
                Column {
                    WidgetPinner.Size.entries.forEach { size ->
                        TextButton(
                            onClick = {
                                pinning = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        WidgetPinner.pin(context.applicationContext, target.id, size)
                                    }
                                    // REQUESTED: the launcher shows its own confirmation, nothing to add here.
                                    val message = when (result) {
                                        WidgetPinner.Result.REQUESTED -> null
                                        WidgetPinner.Result.NOT_SUPPORTED -> R.string.pin_not_supported
                                        WidgetPinner.Result.NOT_READY -> R.string.pin_not_ready
                                        WidgetPinner.Result.FAILED -> R.string.pin_failed
                                    }
                                    if (message != null) snackbar.showSnackbar(context.getString(message))
                                }
                            },
                        ) { Text(stringResource(size.labelRes)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pinning = null }) { Text(stringResource(R.string.rename_cancel)) }
            },
        )
    }

    collecting?.let { targets ->
        CollectionDialog(
            existing = LibraryOrganizer.collections(library),
            initial = targets.map { it.collection }.distinct().singleOrNull().orEmpty(),
            count = targets.size,
            onDismiss = { collecting = null },
            onConfirm = { name ->
                collecting = null
                selection = null
                scope.launch {
                    withContext(Dispatchers.IO) { targets.forEach { store.setCollection(it.id, name) } }
                    refresh()
                }
            },
        )
    }

    val sorted = remember(library, sort) { AnimationSort.sort(library, sort) }
    var collectionFilter by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 with transparency, 2 optimized
    val collectionNames = remember(library) { LibraryOrganizer.collections(library) }
    // A collection that no longer exists (its last GIF moved away) must not leave the library looking empty.
    val activeCollection = if (collectionNames.any { it.equals(collectionFilter, ignoreCase = true) }) collectionFilter else ""
    val shown = remember(sorted, query, filter, usedIds, activeCollection) {
        val needle = query.trim()
        LibraryOrganizer.inCollection(sorted, activeCollection).filter { meta ->
            (needle.isEmpty() || meta.name.contains(needle, ignoreCase = true)) &&
                when (filter) {
                    1 -> meta.hasAlpha
                    2 -> meta.id in usedIds
                    3 -> meta.favorite
                    else -> true
                }
        }
    }
    val backupDays = remember(library, appSnap) {
        val now = System.currentTimeMillis()
        val oldest = library.minOfOrNull { it.createdAt } ?: 0L
        if (BackupNudge.shouldShow(now, appSnap.lastBackupMs, appSnap.backupSnoozeUntilMs, oldest, library.size)) {
            BackupNudge.daysSince(now, if (appSnap.lastBackupMs > 0L) appSnap.lastBackupMs else oldest)
        } else {
            null
        }
    }
    // "Importando 2/5…" while several GIFs are being added; plain "Importando…" for one.
    val importLabel = if (importing && importTotal > 1) {
        stringResource(R.string.import_batch_progress, importCurrent, importTotal)
    } else {
        stringResource(if (importing) R.string.library_importing else R.string.library_import_button)
    }
    val startImport: () -> Unit = {
        if (!importing) {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")),
            )
        }
    }

    val resume: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.IO) { ControlActions.setMaster(context, true) }
            loadGlobal()
        }
    }
    // "Mover igualmente": stop pausing the widgets with battery saver (the same switch as in Settings).
    val moveAnyway: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.IO) {
                val cs = ControlStore(context)
                cs.save(cs.load().copy(pauseOnBatterySaver = false))
                ControlActions.afterChange(context)
            }
            loadGlobal()
        }
    }

    val gridState = rememberLazyGridState()
    val themeController = LocalThemeController.current
    val tips = stringArrayResource(R.array.tips_library).toList()
    BackHandler(enabled = selection != null) { selection = null }
    // Small messages from the top-bar buttons (the season's name, music on or off).
    val say: (String) -> Unit = { text ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    val darkNow = themeController?.dark ?: false
    val styleNow = themeController?.style ?: DesignStyle.PIXEL

    // Kixki says hello the very first time, once.
    if (themeController != null && !themeController.welcomeSeen) {
        KixkiWelcome(onDone = { themeController.setWelcomeSeen(true) })
    }

    Box(modifier = Modifier.fillMaxSize()) {
    SceneBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        // The landscape paints behind the status bar, and the bottom bar handles the navigation bar itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            GifBottomBar(
                items = listOf(
                    // The widgets already on the home screen: which GIF each one uses, and a shortcut to configure it.
                    BarItem(
                        icon = PixelIcons.HOME,
                        label = stringResource(R.string.nav_widgets),
                        selected = false,
                        mainColor = Color(0xFFFF8FC7),
                        secondColor = Color(0xFFFFFAF0),
                        onClick = { context.startActivity(Intent(context, MyWidgetsActivity::class.java)) },
                    ),
                    // The universal switch for every widget's animation (same as the Quick Settings tile). The label says
                    // what pressing does, and the button is highlighted while everything is paused.
                    BarItem(
                        icon = if (masterOn) PixelIcons.PAUSE else PixelIcons.PLAY,
                        label = stringResource(if (masterOn) R.string.main_master_pause else R.string.main_master_resume),
                        selected = !masterOn,
                        mainColor = if (masterOn) Color(0xFFFFD36E) else Color(0xFFFF8FC7),
                        secondColor = Color.White,
                        onClick = {
                            val on = !masterOn
                            masterOn = on
                            scope.launch {
                                withContext(Dispatchers.IO) { ControlActions.setMaster(context, on) }
                                loadGlobal()
                            }
                        },
                    ),
                    BarItem(
                        icon = PixelIcons.WINDOW,
                        label = stringResource(R.string.nav_style),
                        selected = false,
                        mainColor = Color(0xFFFFFAF0),
                        secondColor = Color(0xFF8E63F5),
                        onClick = {
                            themeController?.setStyle(if (styleNow == DesignStyle.XP) DesignStyle.PIXEL else DesignStyle.XP)
                        },
                    ),
                    BarItem(
                        icon = PixelIcons.GEAR,
                        label = stringResource(R.string.main_settings),
                        selected = false,
                        mainColor = Color(0xFFC9B8FF),
                        secondColor = Color.White,
                        onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                    ),
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (library.isEmpty()) {
                LibraryTopBar(say = say)
                PauseBanner(
                    pause = pause,
                    onResume = resume,
                    onMoveAnyway = moveAnyway,
                    onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                EmptyLibrary(onImport = startImport, importing = importing, label = importLabel, tips = tips, modifier = Modifier.weight(1f))
            } else {
                // Kixki strolls over the grid (same box), on the top edge of the windows.
                Box(modifier = Modifier.weight(1f)) {
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The top bar scrolls away with the list: straight to the search box and the GIFs.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.fullBleed(16.dp)) {
                            LibraryTopBar(say = say)
                        }
                    }
                    if (pause != GlobalPause.NONE) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PauseBanner(
                                pause = pause,
                                onResume = resume,
                                onMoveAnyway = moveAnyway,
                                onSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                    if (backupDays != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            BackupBanner(
                                days = backupDays,
                                onNow = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                                onLater = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val cs = ControlStore(context)
                                            cs.save(cs.load().copy(backupSnoozeUntilMs = BackupNudge.snoozeUntil(System.currentTimeMillis())))
                                        }
                                        loadGlobal()
                                    }
                                },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            hint = stringResource(R.string.search_hint),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp, bottom = 4.dp),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PillChip(stringResource(R.string.filter_all), count = library.size, selected = filter == 0) { filter = 0 }
                            PillChip(stringResource(R.string.filter_alpha), count = library.count { it.hasAlpha }, selected = filter == 1) { filter = 1 }
                            PillChip(stringResource(R.string.filter_in_use), count = library.count { it.id in usedIds }, selected = filter == 2) { filter = 2 }
                            if (library.any { it.favorite }) {
                                PillChip(stringResource(R.string.filter_favorites), count = library.count { it.favorite }, selected = filter == 3) { filter = 3 }
                            }
                            collectionNames.forEach { name ->
                                PillChip(
                                    name,
                                    count = library.count { it.collection.equals(name, ignoreCase = true) },
                                    selected = activeCollection.equals(name, ignoreCase = true),
                                ) { collectionFilter = if (activeCollection.equals(name, ignoreCase = true)) "" else name }
                            }
                            PillChip(
                                stringResource(if (selection == null) R.string.select_start else R.string.select_cancel),
                                selected = selection != null,
                            ) { selection = if (selection == null) emptySet() else null }
                        }
                    }
                    if (selection != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val chosen = selection.orEmpty()
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = { selection = shown.map { it.id }.toSet() }) {
                                    Text(stringResource(R.string.select_all))
                                }
                                Button(
                                    onClick = { collecting = shown.filter { it.id in chosen } },
                                    enabled = chosen.isNotEmpty(),
                                ) { Text(stringResource(R.string.select_collection, chosen.size)) }
                                Button(
                                    onClick = { deletingMany = shown.filter { it.id in chosen } },
                                    enabled = chosen.isNotEmpty(),
                                ) { Text(stringResource(R.string.select_delete, chosen.size)) }
                                Button(
                                    onClick = {
                                        val ids = chosen
                                        selection = null
                                        scope.launch {
                                            val suffix = context.getString(R.string.copy_suffix)
                                            withContext(Dispatchers.IO) { ids.forEach { store.duplicate(it, suffix) } }
                                            refresh()
                                            snackbar.showSnackbar(context.getString(R.string.select_duplicated, ids.size))
                                        }
                                    },
                                    enabled = chosen.isNotEmpty(),
                                ) { Text(stringResource(R.string.select_duplicate, chosen.size)) }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SortBar(
                            count = library.size,
                            sort = sort,
                            onSort = { sort = it },
                            compact = compact,
                            onToggleCompact = {
                                val now = !compact
                                compact = now
                                scope.launch(Dispatchers.IO) {
                                    val cs = ControlStore(context)
                                    cs.save(cs.load().copy(compactList = now))
                                }
                            },
                        )
                    }
                    // Like an empty folder on the desktop: the way to add a new GIF.
                    item(key = "add-tile") {
                        AddTile(
                            label = importLabel,
                            enabled = !importing,
                            onClick = startImport,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(R.string.library_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(
                        shown,
                        key = { it.id },
                        span = { if (compact) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    ) { meta ->
                        val actions = CardActions(
                            onEdit = {
                                if (store.hasOriginal(meta.id)) {
                                    context.startActivity(EditorActivity.intent(context, meta.id))
                                } else {
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.library_no_original)) }
                                }
                            },
                            onPin = { pinning = meta },
                            onRename = { renaming = meta },
                            onDuplicate = {
                                scope.launch {
                                    val suffix = context.getString(R.string.copy_suffix)
                                    withContext(Dispatchers.IO) { store.duplicate(meta.id, suffix) }
                                    refresh()
                                }
                            },
                            onDelete = { deleting = meta },
                            onFavorite = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { store.setFavorite(meta.id, !meta.favorite) }
                                    refresh()
                                }
                            },
                            onCollection = { collecting = listOf(meta) },
                        )
                        val toggleSelect = {
                            val cur = selection.orEmpty()
                            selection = if (meta.id in cur) cur - meta.id else cur + meta.id
                        }
                        if (compact) {
                            CompactRow(
                                meta = meta,
                                inUse = meta.id in usedIds,
                                selecting = selection != null,
                                selected = selection?.contains(meta.id) == true,
                                onToggleSelect = toggleSelect,
                                actions = actions,
                            )
                        } else {
                            AnimationCard(
                                meta = meta,
                                lifecycle = lifecycle,
                                inUse = meta.id in usedIds,
                                selecting = selection != null,
                                selected = selection?.contains(meta.id) == true,
                                onToggleSelect = toggleSelect,
                                actions = actions,
                            )
                        }
                    }
                }
                if (!compact) KixkiWalker(gridState, shown.map { it.id }, tips, lifecycle)
                }
            }
        }
    }
    }
}

/** The slim top bar: the logo and three small buttons (day / night, music, season of the background). */
@Composable
private fun LibraryTopBar(say: (String) -> Unit, modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    val design = LocalGifDesign.current
    val seasonNames = stringArrayResource(R.array.season_choice_names)
    val musicOnText = stringResource(R.string.hero_music_on)
    val musicOffText = stringResource(R.string.hero_music_off)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Wordmark(baseCell = 4.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(
                icon = if (design.dark) PixelIcons.SUN else PixelIcons.MOON,
                description = stringResource(R.string.nav_theme),
                onClick = { controller?.setMode(if (design.dark) ThemeMode.LIGHT else ThemeMode.DARK) },
            )
            ToolButton(
                icon = if (controller?.musicOn == true) PixelIcons.MUSIC else PixelIcons.SPEAKER_OFF,
                description = stringResource(R.string.hero_music),
                onClick = {
                    val on = controller?.musicOn != true
                    controller?.setMusic(on)
                    say(if (on) musicOnText else musicOffText)
                },
            )
            ToolButton(
                icon = PixelIcons.STAR,
                description = stringResource(R.string.hero_season),
                onClick = {
                    val next = SeasonLogic.next(controller?.seasonChoice ?: SeasonChoice.AUTO)
                    controller?.setSeasonChoice(next)
                    say(seasonNames[next.ordinal])
                },
            )
        }
    }
}

@Composable
private fun SortBar(
    count: Int,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    compact: Boolean,
    onToggleCompact: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.main_library_title), style = MaterialTheme.typography.titleLarge)
            Tag(count.toString())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(
                label = stringResource(if (compact) R.string.layout_cards else R.string.layout_list),
                onClick = onToggleCompact,
            )
            Box {
                PillChip(label = stringResource(sortName(sort)), onClick = { open = true })
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortName(order))) },
                            onClick = {
                                open = false
                                onSort(order)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun sortName(order: SortOrder): Int = when (order) {
    SortOrder.NEWEST -> R.string.sort_newest
    SortOrder.NAME -> R.string.sort_name
    SortOrder.SIZE -> R.string.sort_size
}

/** What the menu of a GIF can do. */
private class CardActions(
    val onEdit: () -> Unit,
    val onPin: () -> Unit,
    val onRename: () -> Unit,
    val onDuplicate: () -> Unit,
    val onDelete: () -> Unit,
    val onFavorite: () -> Unit,
    val onCollection: () -> Unit,
)

/** The "..." button and its menu, the same on the cards and on the compact rows. */
@Composable
private fun CardMenu(meta: AnimationMeta, actions: CardActions, tint: Color? = null, open: Boolean = false, onOpenChange: ((Boolean) -> Unit)? = null) {
    var own by remember { mutableStateOf(false) }
    val menuOpen = if (onOpenChange != null) open else own
    val setOpen: (Boolean) -> Unit = { if (onOpenChange != null) onOpenChange(it) else own = it }
    Box {
        BarIconButton(icon = PixelIcons.DOTS, onClick = { setOpen(true) }, tint = tint)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { setOpen(false) }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (meta.favorite) R.string.action_unfavorite else R.string.action_favorite)) },
                onClick = { setOpen(false); actions.onFavorite() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_collection)) },
                onClick = { setOpen(false); actions.onCollection() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                onClick = { setOpen(false); actions.onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (meta.isOptimized) R.string.pin_action else R.string.pin_action_locked)) },
                enabled = meta.isOptimized,
                onClick = { setOpen(false); actions.onPin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename_button)) },
                onClick = { setOpen(false); actions.onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_duplicate)) },
                onClick = { setOpen(false); actions.onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { setOpen(false); actions.onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimationCard(
    meta: AnimationMeta,
    lifecycle: Lifecycle,
    inUse: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    actions: CardActions,
) {
    val design = LocalGifDesign.current
    // A little program window: the name in the title bar, the actions behind its "..." button.
    WindowCard(
        title = meta.name,
        modifier = Modifier.fillMaxWidth().then(if (selecting) Modifier.clickable { onToggleSelect() } else Modifier),
        body = cardTint(meta.id),
        // The X of the window asks first, with the same question as "Borrar" in the menu.
        onClose = if (selecting) null else actions.onDelete,
        closeDescription = stringResource(R.string.card_delete_close),
        actions = {
            if (selecting) SelectBox(selected) else CardMenu(meta, actions)
        },
    ) {
        Box(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp).border(3.dp, design.outline)) {
            AnimatedThumb(
                meta = meta,
                lifecycle = lifecycle,
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
        }
        FlowRow(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Short on purpose: frames and speed always; "transparent" only when the pixels really are.
            if (meta.favorite) Tag(stringResource(R.string.tag_favorite))
            if (meta.collection.isNotEmpty()) Tag(meta.collection)
            Tag(stringResource(R.string.tag_frames_short, meta.displayFrames))
            Tag(stringResource(R.string.tag_fps, meta.displayFps))
            if (meta.hasAlpha) Tag(stringResource(R.string.tag_alpha))
            if (inUse) Tag(stringResource(R.string.tag_in_use))
        }
    }
}

/** The compact list mode: one slim row per GIF (still picture, name, a few tags, the same menu). Nothing animates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactRow(
    meta: AnimationMeta,
    inUse: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    actions: CardActions,
) {
    val context = LocalContext.current
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    var thumb by remember(meta.id, meta.editedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(meta.id, meta.editedAt) {
        thumb = withContext(Dispatchers.IO) { ThumbnailStore.loadFirst(context.applicationContext, meta)?.asImageBitmap() }
    }
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else cardTint(meta.id), shape)
            .border(if (selected) design.borderWidth else 3.dp, if (selected) scheme.primary else design.outline, shape)
            .clickable { if (selecting) onToggleSelect() else menuOpen = true }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 72.dp, height = 54.dp).background(Color(0xFF1A0F3A)).border(2.dp, design.outline),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(
                    bitmap = it,
                    contentDescription = meta.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(meta.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (meta.favorite) Tag(stringResource(R.string.tag_favorite))
                if (meta.collection.isNotEmpty()) Tag(meta.collection)
                Tag(stringResource(R.string.tag_frames_short, meta.displayFrames))
                if (inUse) Tag(stringResource(R.string.tag_in_use))
            }
        }
        if (selecting) {
            SelectBox(selected)
        } else {
            CardMenu(meta, actions, tint = scheme.onSurface, open = menuOpen, onOpenChange = { menuOpen = it })
        }
    }
}

/** "Mover a colección": a name to type, or one of the existing ones. Empty = out of any collection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionDialog(
    existing: List<String>,
    initial: String,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    GifDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (count > 1) R.string.collection_title_many else R.string.collection_title, count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NameField(
                    value = text,
                    onValueChange = { text = it.take(LibraryOrganizer.MAX_COLLECTION_LENGTH) },
                    label = stringResource(R.string.collection_label),
                    onDone = { onConfirm(text) },
                )
                if (existing.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        existing.forEach { name ->
                            GifChip(
                                selected = text.equals(name, ignoreCase = true),
                                onClick = { text = name },
                                label = { Text(name, maxLines = 1) },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.collection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.rename_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rename_cancel)) } },
    )
}

/** The reminder to back the library up (see BackupNudge). */
@Composable
private fun BackupBanner(days: Int, onNow: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(design.shadow, design.shadowOffset, shape, downOnly = true)
            .clip(shape)
            .background(scheme.primaryContainer, shape)
            .border(design.borderWidth, design.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.backup_nudge, days), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNow) { Text(stringResource(R.string.backup_nudge_now)) }
            OutlinedButton(onClick = onLater) { Text(stringResource(R.string.backup_nudge_later)) }
        }
    }
}

/** The check box of a window in "Seleccionar" mode. */
@Composable
private fun SelectBox(selected: Boolean) {
    val design = LocalGifDesign.current
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(26.dp)
            .background(if (selected) design.accent else Color(0xFFFFFAF0))
            .border(2.dp, design.titleInk.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) PixelIcon(icon = PixelIcons.CHECK, outline = design.ink, main = design.ink, second = design.ink, size = 18.dp)
    }
}

/** Says why the widgets are still, and offers the one tap that fixes it. Nothing is shown when they move. */
@Composable
private fun PauseBanner(
    pause: GlobalPause,
    onResume: () -> Unit,
    onMoveAnyway: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pause == GlobalPause.NONE) return
    val design = LocalGifDesign.current
    val scheme = MaterialTheme.colorScheme
    val shape = design.cardShape
    val (text, action, onAction) = when (pause) {
        GlobalPause.MASTER_OFF -> Triple(R.string.pause_master, R.string.pause_master_action, onResume)
        GlobalPause.BATTERY_SAVER -> Triple(R.string.pause_battery, R.string.pause_battery_action, onMoveAnyway)
        else -> Triple(R.string.pause_night, R.string.pause_night_action, onSettings)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(design.shadow, design.shadowOffset, shape, downOnly = true)
            .clip(shape)
            .background(scheme.primaryContainer, shape)
            .border(design.borderWidth, design.outline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        Button(onClick = onAction) { Text(stringResource(action)) }
    }
}

private class LoadedThumb(val frames: List<ImageBitmap>, val frameMillis: Long)

/**
 * Plays the cached thumbnail frames. The ticking coroutine lives inside
 * repeatOnLifecycle(STARTED): it is cancelled when the app leaves the screen (and when the
 * card scrolls out of the grid), so nothing keeps running in the background.
 */
@Composable
private fun AnimatedThumb(meta: AnimationMeta, lifecycle: Lifecycle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var thumb by remember(meta.id, meta.editedAt) { mutableStateOf<LoadedThumb?>(null) }
    var index by remember(meta.id, meta.editedAt) { mutableIntStateOf(0) }

    LaunchedEffect(meta.id, meta.editedAt) {
        thumb = withContext(Dispatchers.IO) {
            ThumbnailStore.load(context.applicationContext, meta)?.let { t ->
                LoadedThumb(t.bitmaps.map { it.asImageBitmap() }, t.frameMillis)
            }
        }
    }

    val loaded = thumb
    LaunchedEffect(loaded) {
        if (loaded != null && loaded.frames.size > 1) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(loaded.frameMillis)
                    index = (index + 1) % loaded.frames.size
                }
            }
        }
    }

    Box(modifier = modifier.background(Color(0xFF1A0F3A)), contentAlignment = Alignment.Center) {
        if (loaded != null && loaded.frames.isNotEmpty()) {
            Image(
                bitmap = loaded.frames[index.coerceIn(0, loaded.frames.lastIndex)],
                contentDescription = meta.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None, // crisp pixel art
            )
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    GifDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            NameField(
                value = text,
                onValueChange = { text = it.take(AnimationNames.MAX_LENGTH) },
                label = stringResource(R.string.rename_label),
                onDone = { if (text.isNotBlank()) onConfirm(text) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.rename_cancel)) }
        },
    )
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, importing: Boolean, label: String, tips: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val budgetText = remember {
        val screen = ScreenMetrics.physicalSizePx(context)
        val platformLimit = WidgetMemoryCalculator.platformLimitBytes(screen.width, screen.height)
        val budget = WidgetMemoryCalculator.maxBudgetBytes(screen.width, screen.height)
        val numbers = NumberFormat.getIntegerInstance()
        context.getString(
            R.string.main_memory_budget,
            numbers.format(budget),
            WidgetMemoryCalculator.DEFAULT_USAGE_PERCENT,
            numbers.format(platformLimit),
            screen.width,
            screen.height,
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Kixki says hello here too: tap her for a first tip.
        KixkiCorner(tips)
        Text(
            text = stringResource(R.string.main_empty_state_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = budgetText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.main_memory_budget_unverified),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onImport, enabled = !importing) {
            Text(label)
        }
    }
}
