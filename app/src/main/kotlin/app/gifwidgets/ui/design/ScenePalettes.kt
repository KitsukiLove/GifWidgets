package app.gifwidgets.ui.design

import app.gifwidgets.control.Season

/**
 * Colours of the page's background scenes (its `P` table): 13 values for day and 13 for night, in this order:
 * sky 1-4, hills 1-3, sun/moon (a), trunk (t), detail (o), clouds (c), pine (p), silhouettes (k).
 * Pure Kotlin on purpose (plain ARGB ints) so it can be unit-tested.
 */
class ScenePalette(hex: String) {
    private val v: IntArray = hex.split(' ').map { 0xFF000000.toInt() or it.toInt(16) }.toIntArray()
    val sky1 get() = v[0]
    val sky2 get() = v[1]
    val sky3 get() = v[2]
    val sky4 get() = v[3]
    val g1 get() = v[4]
    val g2 get() = v[5]
    val g3 get() = v[6]
    val a get() = v[7]
    val t get() = v[8]
    val o get() = v[9]
    val c get() = v[10]
    val p get() = v[11]
    val k get() = v[12]
}

object ScenePalettes {
    private val TABLE: Map<String, Array<String>> = mapOf(
        "valle" to arrayOf("5db7f5 7ccaf9 a3dcfb cdeefe 8bdc72 5cc25f 3ea24d ffe27a 7a4a2a ff9fd0 ffffff 2f8f5b 2a1650", "070d2e 0e1a4d 1a2d6d 2b4690 245a4a 1b4a3b 133a2e fff3c4 3b2a22 ffd36e 6f86c8 1f6b45 070d2e"),
        "halloween" to arrayOf("6b2d8f a8408f ee7a4a ffb35c 4a2466 35194d 231235 ffd36e 1b0f2e ff8a1e e9a6c8 1b0f2e 1b0f2e", "0c0620 190b3d 2b1259 46207a 2a1648 1c0f33 120a24 ffb347 07030f ff8a1e 5a3a8a 07030f 07030f"),
        "navidad" to arrayOf("8cc6f0 acd8f6 cbe7fa e8f5ff ffffff e9f2ff d3e4fa fff3b0 6b4226 e8394d ffffff 2f8f5b 2a1650", "070b2a 0f1b4a 1b2f6b 2d4a8f cfe0ff a9c2ee 8aa6d8 fff7d0 4a2d1a ffd36e 8aa6d8 1f6b45 070b2a"),
        "primavera" to arrayOf("8fd3f7 b2e3fa d6f1fd f1fbff a5e682 7fd16a 5bb857 fff09a 7a4a2a ff8fc7 ffffff 3fa05a 2f6f46", "0b1238 142257 233a7a 36508f 2f6a55 245a47 1a4a3a f4f1d0 3b2a22 ff9fd0 6f86c8 26734b 0b1238"),
        "verano" to arrayOf("3fa8f0 69bff6 98d5fb c9ebfd b8e05a 8fcf4a 6db83d ffd23a 7a4a2a ffc21a ffffff 3d9b3d 2a6a2a", "060a26 0d1744 1a2d6d 2e4c94 3d6a3a 2f5a2f 234a26 fff3c4 3b2a22 ffe27a 5a74b8 2b6b30 060a26"),
        "otono" to arrayOf("e9a35c f2bf7c f8d9a3 fdeccb d29a3c b9772c 9a5a24 ffe08a 5a3a22 e8742c fff1d6 d9601f 4a2a15", "1a0f2e 2b1a3f 45274a 63364f 4a3220 3a2819 2a1c12 ffd9a0 2e1d12 e8742c 7a5a8a a8461a 10081a"),
        "xp" to arrayOf("2b6ad6 4b8fe6 7db5f0 c5e3f7 9fe04e 58b52b 2c8a1c fff3c4 79b36f 4fa535 ffffff c9f27e 1e6a16", "060d2e 0e1d52 1a3a82 2c5aa8 2f7a35 22592a 163f1f fff3c4 2a5a55 1d4a28 7d93cc 3e8e48 0c2a12"),
        "xp_primavera" to arrayOf("5aa9ee 86c5f6 b6e0fb e4f5ff b4f070 6ccf3c 2e9a30 fff6c9 8cc79a 62b84a ffffff d6f78e 1e6a16", "0a1038 132460 24408a 3a5ea6 3e8a4a 2a6a3a 1b4a2a f4f1d0 2f6a5a 25623a 8aa0d8 58a860 0c2a12"),
        "xp_verano" to arrayOf("1f7fe0 3f9cf0 79bff8 bfe3fb c5e84a 8cc53a 4f8f22 ffe27a 9cc56e 7fb03a ffffff e6f56a 2f6a1a", "050a2a 0b1850 183a82 2a5aa8 47803a 356a2c 244f1f fff3c4 35633f 2e5a2e 7d93cc 6aa84a 0c2a12"),
        "xp_otono" to arrayOf("e7a05a f2bf85 f9dcb0 fdeed2 e8c04a c98a2a 8f5420 fff0c0 b8905a b5702a fff3e0 f3d070 5a2d12", "1a0f33 2e1a4a 51305a 7a4a5e 7a5a2a 5e4020 3f2a14 ffd9a0 4a3828 4a3220 9a7aa8 a8803a 1a0c06"),
        "xp_invierno" to arrayOf("8fb0d6 b4cbe6 d6e4f2 eef4fa ffffff dce9f8 b2c8e6 fff8dc c6d6ec e6eefa ffffff f4f9ff 47617f", "070b26 101c4e 1e3578 34509c a8c0e8 8aa6d8 6a86bb f4f6ff 5a72a6 7a94c8 9fb2e0 cfe0ff 1a2646"),
        "xp_halloween" to arrayOf("2a1450 6b2d8f d2584a ffa24a 6a4a8a 3f2a5e 241438 ffb04a 3a2358 2e1b48 c98ab8 b283e8 0e0618", "07031a 12082e 241049 3a1a62 40305f 2c2046 1a1230 ff9a2e 2a1b44 1f1436 6a4a8a 8a5fc8 05020c"),
        "xp_navidad" to arrayOf("7fa6d8 a9c6e8 cfe0f4 eef5fc ffffff e4eefb bcd0ee fff6cf c9d9ee eaf1fb ffffff f2f7ff 2f6a4a", "060a28 0c1a50 1a3278 2a4c9a b4c9ec 93aedb 6f8cc0 fff7d0 566fa6 7a94c8 9fb2e0 d6e4ff 142040"),
        "invierno" to arrayOf("a9c8e8 c3daf0 dcebf8 f1f7fd ffffff eaf2fc d6e5f6 fff8d0 5a4636 a9c2ee c9d9ee 2f7f5b 4a3a30", "060a22 0e1a46 1c3068 2f4c8e cfe0ff aac2ee 88a4d8 f4f6ff 2e2a2a 8aa6d8 8aa6d8 1f5f45 1a1622"),
    )

    private fun key(season: Season, xp: Boolean): String = when {
        xp && (season == Season.CLASICA || season == Season.VALLE) -> "xp"
        xp -> "xp_" + season.name.lowercase()
        season == Season.CLASICA -> "valle"
        else -> season.name.lowercase()
    }

    private val cache = HashMap<String, ScenePalette>()

    /** Parsed once per look; the scene asks for it on every frame. */
    @Synchronized
    fun of(season: Season, xp: Boolean, night: Boolean): ScenePalette {
        val k = key(season, xp) + if (night) "/n" else "/d"
        return cache.getOrPut(k) { ScenePalette(TABLE.getValue(key(season, xp))[if (night) 1 else 0]) }
    }
}
