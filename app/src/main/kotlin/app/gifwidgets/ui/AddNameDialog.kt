package app.gifwidgets.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gifwidgets.R
import app.gifwidgets.library.AnimationNames
import app.gifwidgets.ui.design.GifDialog
import app.gifwidgets.ui.design.NameField
import app.gifwidgets.ui.design.TextButton

/** A picked GIF waiting to be added, with the name it will get. */
data class PendingGif(val uri: Uri, val name: String)

/**
 * The quick question before a GIF is added: "what do you want to call it?", with the file name as a suggestion.
 * With several GIFs it is asked one at a time ([position] of [total]); "Añadir todos así" adds the rest with their
 * suggested names. Cancel drops everything that was not added yet.
 */
@Composable
fun AddNameDialog(
    current: PendingGif,
    position: Int,
    total: Int,
    onConfirm: (String) -> Unit,
    onConfirmAll: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(current.uri) { mutableStateOf(current.name) }
    val more = total - position
    GifDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (total > 1) stringResource(R.string.add_name_title_many, position, total)
                else stringResource(R.string.add_name_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NameField(
                    value = text,
                    onValueChange = { text = it.take(AnimationNames.MAX_LENGTH) },
                    label = stringResource(R.string.rename_label),
                    onDone = { if (text.isNotBlank()) onConfirm(text) },
                )
                Text(
                    stringResource(R.string.add_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (more > 0) {
                    TextButton(onClick = { onConfirmAll(text) }, enabled = text.isNotBlank(), modifier = Modifier) {
                        Text(stringResource(R.string.add_name_all, more))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.add_name_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.rename_cancel)) }
        },
    )
}
