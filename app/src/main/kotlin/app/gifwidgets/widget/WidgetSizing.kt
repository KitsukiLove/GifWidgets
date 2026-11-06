package app.gifwidgets.widget

/**
 * Which of the sizes the launcher reports is the widget's real size right now. Pure, so it is tested.
 *
 * The launcher reports TWO shapes for the same widget: portrait (narrow and tall: min width x max height) and
 * landscape (wide and short: max width x min height). Rendering for the landscape shape while the phone is held
 * upright makes a banner-shaped bitmap that the launcher then shrinks inside the real widget: tiny image.
 * So the shape must follow the current orientation of the phone.
 */
object WidgetSizing {

    /** Returns (widthDp, heightDp), or null when the launcher reported nothing usable. */
    fun pick(
        sizes: List<Pair<Float, Float>>,
        minW: Int,
        maxW: Int,
        minH: Int,
        maxH: Int,
        landscape: Boolean,
    ): Pair<Float, Float>? {
        val valid = sizes.filter { it.first >= 1f && it.second >= 1f }
        if (valid.isNotEmpty()) {
            // Aspect ratio = width / height: the smallest is the portrait shape, the largest the landscape one.
            return if (landscape) valid.maxByOrNull { it.first / it.second } else valid.minByOrNull { it.first / it.second }
        }
        val w = if (landscape) maxW else minW
        val h = if (landscape) minH else maxH
        if (w >= 1 && h >= 1) return w.toFloat() to h.toFloat()
        // Some launchers fill only some of the four values.
        val anyW = maxOf(minW, maxW)
        val anyH = maxOf(minH, maxH)
        return if (anyW >= 1 && anyH >= 1) anyW.toFloat() to anyH.toFloat() else null
    }
}
