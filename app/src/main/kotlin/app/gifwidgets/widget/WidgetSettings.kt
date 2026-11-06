package app.gifwidgets.widget

import app.gifwidgets.gif.GifKind
import app.gifwidgets.library.AnimationMeta

/** AUTO = fill the widget when little of the image would be cropped, otherwise fit it whole (see ScaleMath). */
enum class ScaleMode { AUTO, FIT, FILL, CENTER }
enum class ButtonCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }
enum class TapAction {
    /** Pause / resume this widget. */
    TOGGLE,
    /** Universal switch: pauses / resumes every widget (same as the Quick Settings tile). */
    TOGGLE_ALL,
    /** Show the next animation of this widget's list (no timers). */
    NEXT_ANIMATION,
    /** Experimental: the widget rests still and plays one loop when tapped. */
    PLAY_ONCE,
    OPEN_APP,
    NONE,
}

/** Everything one widget instance remembers (stored per appWidgetId). Pure data, no Android. */
data class WidgetSettings(
    /** Library animation shown (must have been saved with "Guardar optimizado"). */
    val animationId: String? = null,
    /** The per-widget manual switch: true = animated, false = still. Stage 5 adds night schedule and battery saver on top. */
    val animating: Boolean = true,
    /** Playback speed multiplier in percent (100 = the saved fps). */
    val speedPercent: Int = 100,
    val scale: ScaleMode = ScaleMode.AUTO,
    /** Extra zoom on top of the scale mode, 50..300 % (100 = none). */
    val zoomPercent: Int = 100,
    val cornerDp: Int = 0,
    val borderDp: Int = 0,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val bgColor: Int = 0xFF000000.toInt(),
    /** 0 = transparent widget background. */
    val bgOpacity: Int = 0,
    val paddingDp: Int = 0,
    val brightness: Int = 100,
    val opacity: Int = 100,
    val sharp: Boolean = true,
    val showButton: Boolean = false,
    val buttonCorner: ButtonCorner = ButtonCorner.BOTTOM_END,
    val tap: TapAction = TapAction.TOGGLE,
    /** Animations cycled by the "next animation" tap (ids; the shown one is [animationId]). */
    val rotation: List<String> = emptyList(),
    /** Stage 5: this widget's choice during ONE night window; only valid while [nightOverrideKey] is the current window key. */
    val nightOverride: Boolean? = null,
    val nightOverrideKey: String? = null,
    /** Stage 5: "play once" is running right now. */
    val playingOnce: Boolean = false,
    /**
     * When "play once" ends (epoch millis). The end alarm can be lost (reboot, force stop), so the clock is the
     * truth: [playingOnce] only counts while now < [playUntilMs]. 0 = not playing.
     */
    val playUntilMs: Long = 0L,
    /**
     * "Máxima calidad": a foreground service plays the animation frame by frame (full resolution, every frame,
     * no memory limit) instead of handing the launcher a limited set of bitmaps. Costs battery.
     */
    val highQuality: Boolean = false,
    /** Automatic rotation: change to the next animation of [rotation] every this many minutes (0 = off). */
    val autoRotateMinutes: Int = 0,
    /** When the automatic rotation last changed this widget (epoch millis; 0 = never). */
    val rotatedAtMs: Long = 0L,
) {
    /** The override if it belongs to the current night window ([windowKey]), else null. */
    fun nightOverrideFor(windowKey: String?): Boolean? =
        if (nightOverride != null && windowKey != null && windowKey == nightOverrideKey) nightOverride else null

    fun sanitized() = copy(
        speedPercent = speedPercent.coerceIn(25, 300),
        zoomPercent = zoomPercent.coerceIn(50, 300),
        cornerDp = cornerDp.coerceIn(0, 48),
        borderDp = borderDp.coerceIn(0, 8),
        bgOpacity = bgOpacity.coerceIn(0, 100),
        paddingDp = paddingDp.coerceIn(0, 24),
        brightness = brightness.coerceIn(50, 150),
        opacity = opacity.coerceIn(10, 100),
        autoRotateMinutes = autoRotateMinutes.coerceIn(0, 7 * 24 * 60),
        rotatedAtMs = rotatedAtMs.coerceAtLeast(0L),
    )

    /** The look of [other] (framing, frame, colours, button) on this widget; its animation, rotation and taps stay. */
    fun withLookFrom(other: WidgetSettings): WidgetSettings = copy(
        scale = other.scale, zoomPercent = other.zoomPercent, paddingDp = other.paddingDp,
        cornerDp = other.cornerDp, borderDp = other.borderDp, borderColor = other.borderColor,
        bgColor = other.bgColor, bgOpacity = other.bgOpacity,
        brightness = other.brightness, opacity = other.opacity, sharp = other.sharp,
        showButton = other.showButton, buttonCorner = other.buttonCorner,
    ).sanitized()

    /** "Restablecer" of each block of the configuration screen: only that block goes back to its default. */
    fun resetPlayback(): WidgetSettings = copy(speedPercent = DEFAULT.speedPercent)

    fun resetFraming(): WidgetSettings = copy(scale = DEFAULT.scale, zoomPercent = DEFAULT.zoomPercent, paddingDp = DEFAULT.paddingDp)

    fun resetFrame(): WidgetSettings = copy(
        cornerDp = DEFAULT.cornerDp, borderDp = DEFAULT.borderDp, borderColor = DEFAULT.borderColor,
        bgColor = DEFAULT.bgColor, bgOpacity = DEFAULT.bgOpacity,
    )

    fun resetColors(): WidgetSettings = copy(brightness = DEFAULT.brightness, opacity = DEFAULT.opacity)

    fun resetButton(): WidgetSettings = copy(showButton = DEFAULT.showButton, buttonCorner = DEFAULT.buttonCorner)

    /** Automatic rotation only does something with two or more animations to cycle. */
    val autoRotates: Boolean get() = autoRotateMinutes > 0 && rotation.size >= 2

    /** Changes when the rendered pixels change (speed, button and tap action do not affect them). */
    fun visualKey(): String =
        "$scale|$zoomPercent|$cornerDp|$borderDp|$borderColor|$bgColor|$bgOpacity|$paddingDp|$brightness|$opacity|$sharp"

    fun toMap(): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        animationId?.let { m["animationId"] = it }
        m["v"] = VERSION
        m["animating"] = animating
        m["speed"] = speedPercent
        m["scale"] = scale.name
        m["zoom"] = zoomPercent
        m["corner"] = cornerDp
        m["border"] = borderDp
        m["borderColor"] = borderColor
        m["bgColor"] = bgColor
        m["bgOpacity"] = bgOpacity
        m["padding"] = paddingDp
        m["brightness"] = brightness
        m["opacity"] = opacity
        m["sharp"] = sharp
        m["showButton"] = showButton
        m["buttonCorner"] = buttonCorner.name
        m["tap"] = tap.name
        if (rotation.isNotEmpty()) m["rotation"] = rotation.joinToString(",")
        nightOverride?.let { m["nightOverride"] = it }
        nightOverrideKey?.let { m["nightOverrideKey"] = it }
        if (playingOnce) m["playingOnce"] = true
        if (playUntilMs > 0L) m["playUntil"] = playUntilMs
        if (highQuality) m["hq"] = true
        if (autoRotateMinutes > 0) m["autoRotate"] = autoRotateMinutes
        if (rotatedAtMs > 0L) m["rotatedAt"] = rotatedAtMs
        return m
    }

    companion object {
        const val VERSION = 2
        private val DEFAULT = WidgetSettings()

        /**
         * First settings of a widget that shows an animation of the given ORIGINAL size: the default look, with
         * crisp pixels only for pixel-art-sized GIFs (and only if "Píxel nítido" is on in Settings). A big GIF
         * scaled with nearest-neighbour looks jagged, so it gets the smooth filter. The framing (AUTO) already
         * adapts to the shape of the GIF and of the widget by itself.
         */
        fun forAnimation(animationId: String, srcWidth: Int, srcHeight: Int, globalSharp: Boolean): WidgetSettings =
            WidgetSettings(animationId = animationId, sharp = globalSharp && GifKind.isPixelArt(srcWidth, srcHeight))

        fun forAnimation(meta: AnimationMeta, globalSharp: Boolean): WidgetSettings =
            forAnimation(meta.id, meta.srcWidth, meta.srcHeight, globalSharp)

        fun fromMap(m: Map<String, Any?>): WidgetSettings {
            val d = WidgetSettings()
            fun int(k: String, def: Int) = (m[k] as? Number)?.toInt() ?: def
            fun bool(k: String, def: Boolean) = m[k] as? Boolean ?: def
            // Settings saved before version 2 had no "auto" size and showed the pause button by default.
            val old = int("v", 1) < 2
            return WidgetSettings(
                animationId = (m["animationId"] as? String)?.takeIf { it.isNotEmpty() },
                animating = bool("animating", d.animating),
                speedPercent = int("speed", d.speedPercent),
                scale = if (old) d.scale else ScaleMode.entries.firstOrNull { it.name == m["scale"] } ?: d.scale,
                zoomPercent = int("zoom", d.zoomPercent),
                cornerDp = int("corner", d.cornerDp),
                borderDp = int("border", d.borderDp),
                borderColor = int("borderColor", d.borderColor),
                bgColor = int("bgColor", d.bgColor),
                bgOpacity = int("bgOpacity", d.bgOpacity),
                paddingDp = int("padding", d.paddingDp),
                brightness = int("brightness", d.brightness),
                opacity = int("opacity", d.opacity),
                sharp = bool("sharp", d.sharp),
                showButton = if (old) d.showButton else bool("showButton", d.showButton),
                buttonCorner = ButtonCorner.entries.firstOrNull { it.name == m["buttonCorner"] } ?: d.buttonCorner,
                tap = TapAction.entries.firstOrNull { it.name == m["tap"] } ?: d.tap,
                rotation = (m["rotation"] as? String).orEmpty().split(',').filter { it.isNotEmpty() },
                nightOverride = m["nightOverride"] as? Boolean,
                nightOverrideKey = m["nightOverrideKey"] as? String,
                playingOnce = bool("playingOnce", false),
                playUntilMs = (m["playUntil"] as? Number)?.toLong() ?: 0L,
                highQuality = bool("hq", false),
                autoRotateMinutes = int("autoRotate", 0),
                rotatedAtMs = (m["rotatedAt"] as? Number)?.toLong() ?: 0L,
            ).sanitized()
        }
    }
}
