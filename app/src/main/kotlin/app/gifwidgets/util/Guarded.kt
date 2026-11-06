package app.gifwidgets.util

/**
 * Runs [block] and swallows whatever it throws.
 *
 * Widget work runs on bare helper threads (goAsync). An exception that escapes such a thread kills the whole
 * app process, even when nobody is looking at it, so every thread body goes through this.
 * Nothing here is critical: the next update, tap or app open simply tries again.
 */
inline fun runGuarded(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        // intentionally ignored (see above)
    } catch (e: OutOfMemoryError) {
        // intentionally ignored (see above)
    }
}

/** Like [runGuarded] but returns the value, or null if the block threw (an exception or running out of memory). */
inline fun <T> runGuardedResult(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
