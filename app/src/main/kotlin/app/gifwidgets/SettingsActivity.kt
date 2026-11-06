package app.gifwidgets

import app.gifwidgets.ui.design.ScreenWindow
import android.app.Activity
import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import app.gifwidgets.ui.design.GifDialog
import app.gifwidgets.ui.design.CollapsibleSection
import app.gifwidgets.ui.design.GifCheckbox
import app.gifwidgets.ui.design.GifChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import app.gifwidgets.ui.design.OutlinedButton
import androidx.compose.material3.Scaffold
import app.gifwidgets.ui.design.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import app.gifwidgets.ui.design.Switch
import androidx.compose.material3.Text
import app.gifwidgets.ui.design.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.gifwidgets.backup.ImportPreview
import app.gifwidgets.backup.LibraryBackup
import app.gifwidgets.control.AnimationsTileService
import app.gifwidgets.control.AppSettings
import app.gifwidgets.control.ControlActions
import app.gifwidgets.control.ControlStore
import app.gifwidgets.audio.KixkiAudio
import app.gifwidgets.control.DesignStyle
import app.gifwidgets.control.SeasonChoice
import app.gifwidgets.control.ThemeMode
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.memory.WidgetShare
import app.gifwidgets.ui.theme.AppTheme
import app.gifwidgets.widget.WidgetStore
import app.gifwidgets.widget.WidgetUpdater
import app.gifwidgets.ui.theme.LocalThemeController
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings: universal switch, night schedule, battery saver, editor defaults, theme, storage per animation,
 * export / import of the library (ZIP) and the Privacy screen.
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenWindow(title = stringResource(R.string.win_settings), onClose = { finish() }) { SettingsScreen() }
                }
            }
        }
    }
}

/** One row of the storage list. */
private class StorageRow(val meta: AnimationMeta, val totalBytes: Long, val originalBytes: Long)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val controller = LocalThemeController.current
    val seasonNames = stringArrayResource(R.array.season_choice_names)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var settings by remember { mutableStateOf(AppSettings()) }
    var tileMessage by remember { mutableStateOf<Int?>(null) }
    var rows by remember { mutableStateOf<List<StorageRow>>(emptyList()) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var importIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var removing by remember { mutableStateOf<StorageRow?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun loadRows() {
        rows = withContext(Dispatchers.IO) {
            val store = LibraryStore(context)
            store.list().map { m ->
                val original = java.io.File(store.dirFor(m.id), LibraryStore.ORIGINAL_FILE)
                StorageRow(m, store.diskBytes(m.id), if (original.isFile) original.length() else 0L)
            }
        }
    }

    LaunchedEffect(Unit) {
        settings = withContext(Dispatchers.IO) { ControlStore(context).load() }
        loadRows()
    }

    /** Saves the settings; [refreshWidgets] also re-arms the night alarm and re-renders the widgets. */
    fun change(new: AppSettings, refreshWidgets: Boolean) {
        settings = new.sanitized()
        val saved = settings
        scope.launch(Dispatchers.IO) {
            ControlStore(context).save(saved)
            if (refreshWidgets) ControlActions.afterChange(context)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = true
            val ids = exportIds.toList()
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { LibraryBackup.export(context, ids, it) } ?: -1
                    } catch (e: java.io.IOException) {
                        -1
                    }
                }
                busy = false
                if (written >= 0) change(settings.copy(lastBackupMs = System.currentTimeMillis(), backupSnoozeUntilMs = 0L), false)
                snackbar.showSnackbar(
                    if (written >= 0) context.getString(R.string.export_done, written) else context.getString(R.string.export_failed),
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                var readFailed = false // the file could not be copied (for example no free space), as opposed to "not a library"
                val result = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { LibraryBackup.prepareImport(context, it) }
                    } catch (e: app.gifwidgets.backup.ZipFormatException) {
                        null
                    } catch (e: java.util.zip.ZipException) {
                        null
                    } catch (e: java.io.IOException) {
                        readFailed = true
                        null
                    } catch (e: RuntimeException) {
                        null
                    }
                }
                busy = false
                if (result == null) {
                    snackbar.showSnackbar(context.getString(if (readFailed) R.string.import_zip_io_failed else R.string.import_zip_invalid))
                } else {
                    preview = result
                    importIds = result.items.map { it.id }.toSet()
                }
            }
        }
    }

    if (exportDialog) {
        GifDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text(stringResource(R.string.export_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.export_help), style = MaterialTheme.typography.bodySmall)
                    rows.forEach { row ->
                        CheckLine(row.meta.name, exportIds.contains(row.meta.id)) { on ->
                            exportIds = if (on) exportIds + row.meta.id else exportIds - row.meta.id
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exportIds.isNotEmpty(),
                    onClick = {
                        exportDialog = false
                        exportLauncher.launch("gifwidgets-${LocalDate.now()}.zip")
                    },
                ) { Text(stringResource(R.string.export_action)) }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text(stringResource(R.string.wc_cancel)) } },
        )
    }

    preview?.let { p ->
        GifDialog(
            onDismissRequest = {
                preview = null
                LibraryBackup.discard(context)
            },
            title = { Text(stringResource(R.string.import_zip_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.import_zip_help), style = MaterialTheme.typography.bodySmall)
                    p.items.forEach { item ->
                        CheckLine(
                            stringResource(R.string.import_zip_item, item.name, item.frames, Formatter.formatShortFileSize(context, item.bytes)),
                            importIds.contains(item.id),
                        ) { on -> importIds = if (on) importIds + item.id else importIds - item.id }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importIds.isNotEmpty() && !busy,
                    onClick = {
                        val ids = importIds
                        preview = null
                        busy = true
                        scope.launch {
                            val (ok, failed) = withContext(Dispatchers.IO) {
                                val r = LibraryBackup.commitImport(context, p, ids)
                                LibraryBackup.discard(context)
                                r
                            }
                            busy = false
                            loadRows()
                            // Empty widgets pick up their first animation.
                            if (ok > 0) app.gifwidgets.widget.GifWidgetProvider.refreshAll(context)
                            snackbar.showSnackbar(context.getString(R.string.import_zip_done, ok, failed))
                        }
                    },
                ) { Text(stringResource(R.string.import_zip_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview = null
                    LibraryBackup.discard(context)
                }) { Text(stringResource(R.string.wc_cancel)) }
            },
        )
    }

    removing?.let { row ->
        GifDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.storage_remove_title, row.meta.name)) },
            text = { Text(stringResource(R.string.storage_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        withContext(Dispatchers.IO) { LibraryStore(context).deleteOriginal(row.meta.id) }
                        loadRows()
                    }
                }) { Text(stringResource(R.string.storage_remove_action)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.wc_cancel)) } },
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

            CollapsibleSection(title = stringResource(R.string.settings_sec_animations), initiallyOpen = true) {
            SwitchBlock(
                stringResource(R.string.settings_master),
                stringResource(R.string.settings_master_help),
                settings.masterEnabled,
            ) { change(settings.copy(masterEnabled = it), true) }

            OutlinedButton(onClick = { tileMessage = requestTile(context) }) {
                Text(stringResource(R.string.settings_tile_add))
            }
            tileMessage?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }

            SwitchBlock(
                stringResource(R.string.settings_night),
                stringResource(R.string.settings_night_help),
                settings.nightEnabled,
            ) { change(settings.copy(nightEnabled = it), true) }
            if (settings.nightEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        pickTime(context, settings.nightStartMin) { change(settings.copy(nightStartMin = it), true) }
                    }) { Text(stringResource(R.string.settings_night_from, formatMinutes(settings.nightStartMin))) }
                    OutlinedButton(onClick = {
                        pickTime(context, settings.nightEndMin) { change(settings.copy(nightEndMin = it), true) }
                    }) { Text(stringResource(R.string.settings_night_to, formatMinutes(settings.nightEndMin))) }
                }
            }

            SwitchBlock(
                stringResource(R.string.settings_battery),
                stringResource(R.string.settings_battery_help),
                settings.pauseOnBatterySaver,
            ) { change(settings.copy(pauseOnBatterySaver = it), true) }
            // Why a GIF may look less sharp when there are many widgets (see WidgetShare).
            var animatedWidgets by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                animatedWidgets = withContext(Dispatchers.IO) {
                    try {
                        WidgetUpdater.animatedWidgetCount(context.applicationContext)
                    } catch (e: RuntimeException) {
                        0
                    }
                }
            }
            Text(
                stringResource(
                    R.string.settings_widgets_memory,
                    animatedWidgets,
                    minOf(WidgetStore.DEFAULT_USAGE, WidgetShare.usagePercent(animatedWidgets)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            SwitchBlock(
                stringResource(R.string.settings_play_once),
                stringResource(R.string.settings_play_once_help),
                settings.playOnceEnabled,
            ) { change(settings.copy(playOnceEnabled = it), false) }

            }

            CollapsibleSection(title = stringResource(R.string.settings_sec_editor)) {
            Text(stringResource(R.string.settings_editor_section), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_default_fps, settings.defaultFps))
            Slider(
                value = settings.defaultFps.toFloat(),
                onValueChange = { settings = settings.copy(defaultFps = it.roundToInt()).sanitized() },
                onValueChangeFinished = { change(settings, false) },
                valueRange = 5f..25f,
            )
            SwitchBlock(
                stringResource(R.string.wc_sharp),
                stringResource(R.string.settings_sharp_help),
                settings.sharpPixels,
            ) { change(settings.copy(sharpPixels = it), false) }
            SwitchBlock(
                stringResource(R.string.settings_keep_originals),
                stringResource(R.string.settings_keep_originals_help),
                settings.keepOriginals,
            ) { change(settings.copy(keepOriginals = it), false) }

            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ThemeMode.entries.forEach { mode ->
                    GifChip(
                        selected = settings.theme == mode,
                        onClick = {
                            settings = settings.copy(theme = mode)
                            val saved = settings
                            scope.launch {
                                withContext(Dispatchers.IO) { ControlStore(context).save(saved) }
                                // Restart this screen so it shows the new theme right away.
                                (context as? Activity)?.recreate()
                            }
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                                        ThemeMode.LIGHT -> R.string.settings_theme_light
                                        ThemeMode.DARK -> R.string.settings_theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            Text(stringResource(R.string.settings_style), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                DesignStyle.entries.forEach { style ->
                    GifChip(
                        selected = settings.style == style,
                        onClick = {
                            settings = settings.copy(style = style)
                            val saved = settings
                            scope.launch {
                                withContext(Dispatchers.IO) { ControlStore(context).save(saved) }
                                // Restart this screen so it shows the new style right away.
                                (context as? Activity)?.recreate()
                            }
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (style) {
                                        DesignStyle.PIXEL -> R.string.style_pixel
                                        DesignStyle.XP -> R.string.style_xp
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            }

            CollapsibleSection(title = stringResource(R.string.settings_scenery_section)) {
            Text(stringResource(R.string.settings_season), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_season_help), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SeasonChoice.entries.forEach { choice ->
                    GifChip(
                        selected = settings.season == choice,
                        onClick = {
                            KixkiAudio.blip(660.0, .06)
                            settings = settings.copy(season = choice)
                            controller?.setSeasonChoice(choice)
                        },
                        label = { Text(seasonNames[choice.ordinal]) },
                    )
                }
            }
            SwitchBlock(
                stringResource(R.string.settings_anim_bg),
                stringResource(R.string.settings_anim_bg_help),
                settings.animatedBackground,
            ) {
                settings = settings.copy(animatedBackground = it)
                controller?.setAnimatedBackground(it)
            }
            SwitchBlock(
                stringResource(R.string.settings_tips),
                stringResource(R.string.settings_tips_help),
                settings.tipsOn,
            ) {
                settings = settings.copy(tipsOn = it)
                controller?.setTips(it)
            }
            SwitchBlock(
                stringResource(R.string.settings_music),
                stringResource(R.string.settings_music_help),
                settings.musicOn,
            ) {
                settings = settings.copy(musicOn = it)
                controller?.setMusic(it)
            }
            SwitchBlock(
                stringResource(R.string.settings_sound),
                stringResource(R.string.settings_sound_help),
                settings.soundOn,
            ) {
                settings = settings.copy(soundOn = it)
                controller?.setSound(it)
            }

            }

            CollapsibleSection(
                title = stringResource(R.string.settings_storage_section),
                summary = if (rows.isNotEmpty()) Formatter.formatShortFileSize(context, rows.sumOf { it.totalBytes }) else null,
            ) {
            if (rows.isEmpty()) {
                Text(stringResource(R.string.settings_storage_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    stringResource(R.string.settings_storage_total, Formatter.formatShortFileSize(context, rows.sumOf { it.totalBytes })),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.meta.name, maxLines = 1)
                        Text(
                            stringResource(
                                R.string.settings_storage_item,
                                Formatter.formatShortFileSize(context, row.totalBytes),
                                Formatter.formatShortFileSize(context, row.originalBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (row.originalBytes > 0 && row.meta.isOptimized) {
                        TextButton(onClick = { removing = row }) { Text(stringResource(R.string.storage_remove_short)) }
                    }
                }
            }

            }

            CollapsibleSection(title = stringResource(R.string.settings_backup_section)) {
            Text(stringResource(R.string.settings_backup_help), style = MaterialTheme.typography.bodySmall)
            Text(
                if (settings.lastBackupMs > 0L) {
                    stringResource(
                        R.string.settings_backup_last,
                        java.time.Instant.ofEpochMilli(settings.lastBackupMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    )
                } else {
                    stringResource(R.string.settings_backup_never)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = rows.isNotEmpty() && !busy,
                    onClick = {
                        exportIds = rows.map { it.meta.id }.toSet()
                        exportDialog = true
                    },
                ) { Text(stringResource(R.string.export_title)) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                ) { Text(stringResource(R.string.import_zip_title)) }
            }

            }

            OutlinedButton(onClick = { context.startActivity(Intent(context, PrivacyActivity::class.java)) }) {
                Text(stringResource(R.string.privacy_title))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

/** A switch whose whole row is one touch target (at least 48 dp tall) and reads as a single switch to TalkBack. */
@Composable
private fun SwitchBlock(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 48.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = checked, onCheckedChange = null)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(help, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CheckLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GifCheckbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun formatMinutes(min: Int): String =
    LocalTime.of(min / 60, min % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun pickTime(context: Context, currentMin: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        currentMin / 60, currentMin % 60, DateFormat.is24HourFormat(context),
    ).show()
}

/** Android 13+ can ask the system to add the tile; older versions show how to do it by hand. Returns a message string id. */
private fun requestTile(context: Context): Int {
    if (Build.VERSION.SDK_INT < 33) return R.string.settings_tile_manual
    val manager = context.getSystemService(StatusBarManager::class.java) ?: return R.string.settings_tile_manual
    return try {
        manager.requestAddTileService(
            ComponentName(context, AnimationsTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile_animation),
            context.mainExecutor,
        ) { /* result code: the system shows its own confirmation */ }
        R.string.settings_tile_asked
    } catch (e: RuntimeException) {
        R.string.settings_tile_manual
    }
}
