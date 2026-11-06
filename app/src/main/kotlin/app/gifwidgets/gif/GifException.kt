package app.gifwidgets.gif

/** Any problem with a GIF we were asked to read. Never crashes the app: callers show [reason]. */
class GifException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        NOT_GIF,
        FILE_TOO_LARGE,
        DIMENSIONS_TOO_BIG,
        TOO_MANY_FRAMES,
        TRUNCATED,
        CORRUPT,
        NO_FRAMES,
    }
}
