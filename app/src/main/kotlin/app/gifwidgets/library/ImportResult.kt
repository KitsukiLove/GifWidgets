package app.gifwidgets.library

enum class FailureReason {
    NOT_GIF,
    FILE_TOO_LARGE,
    DIMENSIONS_TOO_BIG,
    TOO_MANY_FRAMES,
    CORRUPT,
    CANNOT_READ,
    OUT_OF_MEMORY,
}

sealed interface ImportResult {
    data class Success(val meta: AnimationMeta) : ImportResult
    data class Failure(val reason: FailureReason) : ImportResult
}
