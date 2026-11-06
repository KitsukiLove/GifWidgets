package app.gifwidgets.library

/** Turns an arbitrary file name into a safe display name. Pure. */
object AnimationNames {
    const val MAX_LENGTH = 60

    fun sanitize(raw: String?, fallback: String): String {
        var s = raw ?: return fallback
        // Drop a trailing .gif (any case).
        if (s.length > 4 && s.endsWith(".gif", ignoreCase = true)) s = s.dropLast(4)
        val cleaned = StringBuilder()
        for (ch in s) {
            cleaned.append(if (ch.isISOControl() || ch == '/' || ch == '\\') ' ' else ch)
        }
        val collapsed = cleaned.toString().trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return fallback
        return if (collapsed.length > MAX_LENGTH) collapsed.take(MAX_LENGTH).trimEnd() else collapsed
    }

    /**
     * A friendlier first guess for the "name this GIF" question: no ".gif", underscores and dashes become spaces,
     * long number tails ("...1699999999") are dropped, first letter upper-case. Falls back like [sanitize].
     */
    fun suggest(raw: String?, fallback: String): String {
        val base = sanitize(raw, fallback)
        if (base == fallback) return fallback
        var s = base.replace(Regex("[_\\-]+"), " ").replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("\\s*\\d{6,}$"), "").trim()
        if (s.isEmpty()) return fallback
        s = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (s.length > MAX_LENGTH) s.take(MAX_LENGTH).trimEnd() else s
    }
}
