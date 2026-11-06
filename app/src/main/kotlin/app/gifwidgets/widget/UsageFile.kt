package app.gifwidgets.widget

/**
 * usage.txt of a widget: the memory level (percent) it last rendered with, and the "share" (the limit set by how
 * many widgets animate, see WidgetShare) that level was reached under. A level that the system forced under a
 * tight share must not stay forever once the share is bigger again, so it only counts under the same share.
 * Old files hold just the percent (valid under any share). Pure Kotlin.
 */
object UsageFile {
    data class Value(val percent: Int, val share: Int?)

    fun format(percent: Int, share: Int): String = "$percent,$share"

    fun parse(text: String?): Value? {
        val parts = text?.trim()?.split(',') ?: return null
        val percent = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        return Value(percent, parts.getOrNull(1)?.trim()?.toIntOrNull())
    }

    /** The level to start from now: the stored one when it was reached under [share] (or is an old file), else [default]. */
    fun levelFor(stored: Value?, share: Int, default: Int): Int =
        if (stored == null || (stored.share != null && stored.share != share)) default else stored.percent
}
