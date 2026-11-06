package app.gifwidgets

import app.gifwidgets.ui.design.ScreenWindow
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import app.gifwidgets.ui.design.Button
import app.gifwidgets.ui.design.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import app.gifwidgets.ui.design.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.gifwidgets.library.AnimationNames
import app.gifwidgets.library.GifImporter
import app.gifwidgets.library.GifQuickAdd
import app.gifwidgets.widget.GifWidgetProvider
import app.gifwidgets.ui.messageRes
import app.gifwidgets.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface ImportUiState {
    data object Working : ImportUiState
    data class Done(val text: String) : ImportUiState
    data class Failed(@StringRes val messageRes: Int) : ImportUiState
}

/**
 * Share target ("Share" > GifWidgets). Imports the shared GIF and shows the result.
 * Exported (other apps start it), so it treats the incoming file as untrusted:
 * all checks live in [GifImporter] (called by [GifQuickAdd]). It needs no permission: the sender grants read access.
 * configChanges keeps the import running through rotation instead of restarting it.
 */
class ImportActivity : ComponentActivity() {

    private companion object {
        const val MAX_SHARED = 20
    }

    private var state: ImportUiState by mutableStateOf(ImportUiState.Working)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // One shared GIF, or several at once (the sender is untrusted, so only the first few are taken).
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty().take(MAX_SHARED)
            else -> emptyList()
        }

        when {
            // The process was killed and the system recreated us: do not import twice.
            savedInstanceState != null ->
                state = ImportUiState.Failed(R.string.import_error_interrupted)
            uris.isEmpty() ->
                state = ImportUiState.Failed(R.string.import_error_no_file)
            else -> {
                val fallbackName = getString(R.string.default_animation_name)
                val appContext = applicationContext
                lifecycleScope.launch {
                    // One after the other (never in parallel: memory). Each one is imported and saved optimized in one
                    // go, so it is ready to use as a widget.
                    var imported = 0
                    var lastName = ""
                    var failure: Int? = null
                    for (uri in uris) {
                        val result = withContext(Dispatchers.Default) {
                            GifQuickAdd.add(
                                appContext, uri, fallbackName,
                                // Shared GIFs are added without asking: the name is a tidy version of the file name.
                                nameOverride = AnimationNames.suggest(GifImporter.displayName(appContext, uri), fallbackName),
                            )
                        }
                        when (result) {
                            is GifQuickAdd.Result.Success -> { imported++; lastName = result.meta.name }
                            is GifQuickAdd.Result.ImportFailed -> failure = result.reason.messageRes()
                            is GifQuickAdd.Result.SaveFailed -> failure = result.reason.messageRes()
                        }
                    }
                    state = if (imported == 0) {
                        ImportUiState.Failed(failure ?: R.string.import_batch_error)
                    } else {
                        GifWidgetProvider.refreshAll(appContext) // empty widgets pick up their first animation
                        ImportUiState.Done(
                            when {
                                uris.size == 1 -> getString(R.string.import_done, lastName)
                                imported == uris.size -> getString(R.string.import_batch_done, imported)
                                else -> getString(R.string.import_batch_partial, imported, uris.size)
                            },
                        )
                    }
                }
            }
        }

        setContent {
            AppTheme(lifecycle) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenWindow(title = stringResource(R.string.win_import), onClose = { finish() }) {
                        ImportScreen(
                            state = state,
                            onOpenApp = {
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            },
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportScreen(state: ImportUiState, onOpenApp: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            ImportUiState.Working -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.library_importing), style = MaterialTheme.typography.bodyLarge)
            }
            is ImportUiState.Done -> {
                Text(
                    state.text,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenApp) { Text(stringResource(R.string.import_open_app)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.import_close)) }
            }
            is ImportUiState.Failed -> {
                Text(
                    stringResource(state.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onClose) { Text(stringResource(R.string.import_close)) }
            }
        }
    }
}
