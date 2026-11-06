package app.gifwidgets.widget

/**
 * Pure rules (no Android) for stage 4b.
 *
 * "Pending assignment": the library asks the launcher to pin a widget and remembers which animation the
 * user picked, so the new widget's configuration can preselect it. It is stored as "<animationId>|<savedAtMillis>"
 * and expires, so a forgotten request never changes a widget added from the widget picker much later.
 */
object PendingAssignmentLogic {
    const val TTL_MS = 5 * 60_000L

    fun encode(animationId: String, savedAt: Long): String = "$animationId|$savedAt"

    /** The animation id if [text] is well formed and still fresh at [now], otherwise null. The id is NOT validated here. */
    fun decode(text: String, now: Long): String? {
        val parts = text.trim().split('|')
        if (parts.size != 2 || parts[0].isEmpty()) return null
        val savedAt = parts[1].toLongOrNull() ?: return null
        // A clock that moved backwards counts as expired (we cannot trust the age).
        if (now < savedAt || now - savedAt > TTL_MS) return null
        return parts[0]
    }
}

/**
 * When to (re)publish the widget-picker preview (Android 15+ setWidgetPreview is limited to about two calls
 * per hour per provider). [signature] identifies what the preview shows (animation id + last edit), or [NONE]
 * when the library has no optimized animation.
 */
object PreviewPolicy {
    const val NONE = "none"

    fun signature(animationId: String?, editedAt: Long): String =
        if (animationId == null) NONE else "$animationId:$editedAt"

    /**
     * @param done the signature of the preview that was last set successfully (null = never)
     * @param lastAttempt when we last called the system for this provider (0 = never), also after a failure
     */
    fun shouldAttempt(done: String?, lastAttempt: Long, signature: String, now: Long, retryMs: Long): Boolean {
        if (signature == NONE && done == null) return false // nothing was ever set, so nothing to remove
        if (done == signature) return false // already showing the right thing
        return lastAttempt == 0L || now < lastAttempt || now - lastAttempt >= retryMs
    }
}
