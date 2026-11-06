package app.gifwidgets.gif

/** Tiny GIFs generated with Pillow, embedded as hex so tests need no files. */
object TestGifs {
    fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** 4x4, two frames: frame 0 solid red (delay 100 ms), frame 1 solid blue (200 ms), loops forever. */
    val TWO_FRAMES: ByteArray = hex(
        "47494638396104000400810000ff00000000ff00000000000021ff0b4e45545343415045322e30030100000021f90400" +
        "0a0000002c00000000040004000008090001081c48b02080800021f90400140000002c000000000400040081ff000000" +
        "00ff00000000000008090003081c48b0608080003b"
    )

    /** 8x16, ONE interlaced frame. Row r (top to bottom) is solid grey level r * 16. */
    val INTERLACED: ByteArray = hex(
        "47494638376108001000830000000000101010202020303030404040505050606060707070808080909090a0a0a0b0b0" +
        "b0c0c0c0d0d0d0e0e0e0f0f0f02c000000000800100000084b0001081c283080c1830605285ca87080c3870e09489c28" +
        "b180c58b160d68dca8f180c78f1e11881c293281c9932615a85ca97281cb972e19c89c29b381cd9b361de8dca9f381cf" +
        "9f3e0302003b"
    )

    /** 4x4, one frame, colour index 1 (green) is transparent: left half transparent, right half red. */
    val TRANSPARENT: ByteArray = hex(
        "47494638396104000400810000ff000000ff0000000000000021f90401000001002c000000000400040000080e000304" +
        "0000402041830507020808003b"
    )
}
