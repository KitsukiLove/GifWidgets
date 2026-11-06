package app.gifwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.gifwidgets.control.Block
import app.gifwidgets.control.ControlLogic
import app.gifwidgets.control.ControlState
import app.gifwidgets.library.AnimationMeta
import app.gifwidgets.library.LibraryStore
import app.gifwidgets.library.ThumbnailStore
import app.gifwidgets.ui.design.Button
import app.gifwidgets.ui.design.ScreenWindow
import app.gifwidgets.ui.design.Tag
import app.gifwidgets.ui.design.WindowCard
import app.gifwidgets.ui.design.cardTint
import app.gifwidgets.ui.theme.AppTheme
import app.gifwidgets.ui.theme.LocalGifDesign
import app.gifwidgets.util.runGuardedResult
import app.gifwidgets.widget.WidgetConfigureActivity
import app.gifwidgets.widget.WidgetStore
import app.gifwidgets.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Widgets": the widgets already on the home screen, which GIF each one shows, and a shortcut to configure it. */
class MyWidgetsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenWindow(title = stringResource(R.string.win_widgets), onClose = { finish() }) {
                        MyWidgetsScreen(lifecycle)
                    }
                }
            }
        }
    }
}

private class WidgetRow(
    val id: Int,
    val meta: AnimationMeta?,
    val stateRes: Int,
)

private fun loadRows(context: Context): List<WidgetRow> {
    val app = context.applicationContext
    val manager = AppWidgetManager.getInstance(app)
    val store = WidgetStore(app)
    val library = LibraryStore(app)
    return WidgetUpdater.allIds(app, manager).sorted().map { id ->
        val settings = if (store.hasSettings(id)) store.loadSettings(id) else null
        val meta = settings?.animationId?.takeIf { AnimationMeta.isValidId(it) }?.let { library.read(it) }
        val state = when {
            settings == null || meta == null -> R.string.mw_state_still
            else -> {
                val inputs = ControlState.inputs(app, settings)
                when (ControlLogic.blockedBy(inputs)) {
                    Block.MASTER_OFF -> R.string.mw_state_master
                    Block.BATTERY_SAVER -> R.string.mw_state_battery
                    Block.NONE -> when {
                        !ControlLogic.current(inputs) -> R.string.mw_state_still
                        settings.highQuality -> R.string.mw_state_hq
                        else -> R.string.mw_state_playing
                    }
                }
            }
        }
        WidgetRow(id, meta, state)
    }
}

@Composable
private fun MyWidgetsScreen(lifecycle: Lifecycle) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<WidgetRow>?>(null) }
    // Reloaded every time the screen comes back (for example after configuring one).
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            rows = withContext(Dispatchers.IO) { runGuardedResult { loadRows(context) } ?: emptyList() }
        }
    }
    val list = rows ?: return
    if (list.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.mw_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.mw_empty_help), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.mw_count, list.size), style = MaterialTheme.typography.titleMedium) }
        items(list, key = { it.id }) { row -> WidgetCard(row) }
    }
}

@Composable
private fun WidgetCard(row: WidgetRow) {
    val context = LocalContext.current
    val design = LocalGifDesign.current
    var thumb by remember(row.meta?.id, row.meta?.editedAt) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(row.meta?.id, row.meta?.editedAt) {
        val meta = row.meta
        thumb = if (meta == null) null else withContext(Dispatchers.IO) {
            ThumbnailStore.loadFirst(context.applicationContext, meta)?.asImageBitmap()
        }
    }
    WindowCard(
        title = row.meta?.name ?: stringResource(R.string.mw_widget_name, row.id),
        modifier = Modifier.fillMaxWidth(),
        body = cardTint(row.meta?.id ?: row.id.toString()),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(84.dp)
                    .background(Color(0xFF1A0F3A))
                    .border(3.dp, design.outline),
                contentAlignment = Alignment.Center,
            ) {
                thumb?.let {
                    Image(
                        bitmap = it,
                        contentDescription = row.meta?.name,
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.meta == null) Text(stringResource(R.string.mw_no_gif), style = MaterialTheme.typography.bodyMedium)
                Tag(stringResource(row.stateRes))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, WidgetConfigureActivity::class.java)
                                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, row.id),
                        )
                    },
                ) { Text(stringResource(R.string.mw_configure)) }
            }
        }
    }
}
