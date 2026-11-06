package app.gifwidgets.ui

import androidx.annotation.StringRes
import app.gifwidgets.R
import app.gifwidgets.edit.SaveFailure
import app.gifwidgets.library.FailureReason

@StringRes
fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.NOT_GIF -> R.string.import_error_not_gif
    FailureReason.FILE_TOO_LARGE -> R.string.import_error_too_large
    FailureReason.DIMENSIONS_TOO_BIG -> R.string.import_error_dimensions
    FailureReason.TOO_MANY_FRAMES -> R.string.import_error_frames
    FailureReason.CORRUPT -> R.string.import_error_corrupt
    FailureReason.CANNOT_READ -> R.string.import_error_cannot_read
    FailureReason.OUT_OF_MEMORY -> R.string.import_error_memory
}

@StringRes
fun SaveFailure.messageRes(): Int = when (this) {
    SaveFailure.CANNOT_READ -> R.string.editor_error_read
    SaveFailure.CORRUPT -> R.string.editor_error_corrupt
    SaveFailure.OUT_OF_MEMORY -> R.string.editor_error_memory
    SaveFailure.ENCODE_FAILED -> R.string.editor_error_encode
}
