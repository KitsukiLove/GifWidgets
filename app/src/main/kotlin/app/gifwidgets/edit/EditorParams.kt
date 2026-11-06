package app.gifwidgets.edit

/** Everything the editor remembers, so an animation can be re-edited from original.gif without cumulative loss. */
data class EditorParams(
    val background: BackgroundParams = BackgroundParams(),
    val timeline: TimelineParams = TimelineParams(),
    val image: ImageParams = ImageParams(),
    /** true = lossless WebP (default). false = lossy, only used for images without transparency. */
    val lossless: Boolean = true,
    /** 80..95, only for lossy. */
    val quality: Int = 90,
    /** "Ahorrar memoria": the widget may use RGB_565 (2 bytes/pixel) when the image has no transparency. VERIFY visually. */
    val saveMemory: Boolean = false,
) {
    /** Flat map of ints, booleans and int lists, ready for JSON (see AnimationMeta). */
    fun toMap(): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        m["bgMode"] = background.mode.name
        m["bgKey"] = background.keyColor
        m["bgTolerance"] = background.tolerance
        m["bgContiguous"] = background.contiguous
        m["bgSoften"] = background.edgeSoften
        m["bgDefringe"] = background.defringe
        m["bgBlackThreshold"] = background.blackThreshold
        m["bgBlackGain"] = background.blackGain
        m["tTrimStart"] = timeline.trimStart
        m["tTrimEnd"] = timeline.trimEnd
        m["tDeleted"] = timeline.deleted.sorted()
        m["tReverse"] = timeline.reverse
        m["tPingPong"] = timeline.pingPong
        m["tKeepN"] = timeline.keepOneOfN
        m["tFps"] = timeline.fps
        m["tRest"] = timeline.restFrame
        m["iAutoCrop"] = image.autoCrop
        image.manualCrop?.let { m["iCrop"] = listOf(it.left, it.top, it.right, it.bottom) }
        m["iMaxSide"] = image.maxSide
        m["iSharp"] = image.sharpPixels
        m["lossless"] = lossless
        m["quality"] = quality
        m["saveMemory"] = saveMemory
        return m
    }

    companion object {
        /** Tolerant reader: missing or wrongly typed keys fall back to the defaults. */
        fun fromMap(m: Map<String, Any?>): EditorParams {
            val d = EditorParams()
            fun int(k: String, def: Int) = (m[k] as? Number)?.toInt() ?: def
            fun bool(k: String, def: Boolean) = m[k] as? Boolean ?: def
            val mode = (m["bgMode"] as? String)
                ?.let { s -> BackgroundMode.entries.firstOrNull { it.name == s } } ?: d.background.mode
            val deleted = (m["tDeleted"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
            val crop = (m["iCrop"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
                ?.takeIf { it.size == 4 }
                ?.let { CropRect(it[0], it[1], it[2], it[3]) }
            return EditorParams(
                background = BackgroundParams(
                    mode = mode,
                    keyColor = int("bgKey", d.background.keyColor),
                    tolerance = int("bgTolerance", d.background.tolerance),
                    contiguous = bool("bgContiguous", d.background.contiguous),
                    edgeSoften = int("bgSoften", d.background.edgeSoften),
                    defringe = bool("bgDefringe", d.background.defringe),
                    blackThreshold = int("bgBlackThreshold", d.background.blackThreshold),
                    blackGain = int("bgBlackGain", d.background.blackGain),
                ).sanitized(),
                timeline = TimelineParams(
                    trimStart = int("tTrimStart", 0),
                    trimEnd = int("tTrimEnd", -1),
                    deleted = deleted,
                    reverse = bool("tReverse", false),
                    pingPong = bool("tPingPong", false),
                    keepOneOfN = int("tKeepN", 1).coerceIn(1, 100),
                    fps = int("tFps", TimelineParams.DEFAULT_FPS)
                        .coerceIn(TimelineParams.MIN_FPS, TimelineParams.MAX_FPS),
                    restFrame = int("tRest", 0).coerceAtLeast(0),
                ),
                image = ImageParams(
                    autoCrop = bool("iAutoCrop", false),
                    manualCrop = crop,
                    maxSide = int("iMaxSide", 0).coerceIn(0, 4096),
                    sharpPixels = bool("iSharp", true),
                ),
                lossless = bool("lossless", true),
                quality = int("quality", 90).coerceIn(1, 100),
                saveMemory = bool("saveMemory", false),
            )
        }
    }
}
