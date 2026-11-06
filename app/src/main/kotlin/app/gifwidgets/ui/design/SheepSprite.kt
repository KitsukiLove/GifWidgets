package app.gifwidgets.ui.design

// Kixki, the pixel sheep guide of the KixKi Store page (20 x 15 grid, facing right, the same artwork as the page:
// wool, small peach face, star on top, scarf in the theme's main colour, dark hooves).
// Each entry: colour, then groups of x, y, width, height.
internal object SheepSprite {
    const val WIDTH = 20
    const val HEIGHT = 15

    /** Eyes: the "blink" paints them with the face colour. */
    const val EYE_COLOR = 0xFF2B1A5EL
    const val FACE_COLOR = 0xFFF7B58AL

    /** colour (0xAARRGGBB) to flat list of x, y, w, h rectangles. Day theme: purple scarf. */
    private val LIGHT: List<Pair<Long, IntArray>> = listOf(
        0xFFFFC23AL to intArrayOf(16,0,1,1,15,1,3,1,15,2,1,1),
        0xFFFFFAF0L to intArrayOf(4,2,3,1,12,2,3,1,16,2,3,1,2,3,7,1,10,3,9,1,1,4,18,1,1,5,18,1,0,6,14,1,0,7,14,1,0,8,15,1,0,9,11,1,1,10,11,1,2,11,12,1),
        0xFFD98F66L to intArrayOf(14,6,1,1,14,7,1,1),
        0xFFF7B58AL to intArrayOf(15,6,1,1,18,6,1,1,15,7,1,1,18,7,1,1,16,8,1,1,18,8,1,1,16,9,3,1),
        0xFFFFFFFFL to intArrayOf(16,6,1,1),
        0xFF2B1A5EL to intArrayOf(17,6,1,1,16,7,2,1),
        0xFFFF9FB8L to intArrayOf(15,8,1,1),
        0xFFFF8FC7L to intArrayOf(17,8,1,1),
        0xFF6B3FE0L to intArrayOf(11,9,5,1,12,10,1,1),
        0xFFD9C9F5L to intArrayOf(0,10,1,1,1,11,1,1,2,12,12,1),
        0xFF3F1FA0L to intArrayOf(13,10,4,1,14,11,2,1),
        0xFF5A3D7AL to intArrayOf(3,13,2,1,7,13,2,1,3,14,2,1,7,14,2,1),
    )

    /** Night theme: the page turns the scarf gold. */
    private val DARK: List<Pair<Long, IntArray>> = listOf(
        0xFFFFC23AL to intArrayOf(16,0,1,1,15,1,3,1,15,2,1,1),
        0xFFFFFAF0L to intArrayOf(4,2,3,1,12,2,3,1,16,2,3,1,2,3,7,1,10,3,9,1,1,4,18,1,1,5,18,1,0,6,14,1,0,7,14,1,0,8,15,1,0,9,11,1,1,10,11,1,2,11,12,1),
        0xFFD98F66L to intArrayOf(14,6,1,1,14,7,1,1),
        0xFFF7B58AL to intArrayOf(15,6,1,1,18,6,1,1,15,7,1,1,18,7,1,1,16,8,1,1,18,8,1,1,16,9,3,1),
        0xFFFFFFFFL to intArrayOf(16,6,1,1),
        0xFF2B1A5EL to intArrayOf(17,6,1,1,16,7,2,1),
        0xFFFF9FB8L to intArrayOf(15,8,1,1),
        0xFFFF8FC7L to intArrayOf(17,8,1,1),
        0xFFFFD36EL to intArrayOf(11,9,5,1,12,10,1,1),
        0xFFD9C9F5L to intArrayOf(0,10,1,1,1,11,1,1,2,12,12,1),
        0xFF7A5313L to intArrayOf(13,10,4,1,14,11,2,1),
        0xFF5A3D7AL to intArrayOf(3,13,2,1,7,13,2,1,3,14,2,1,7,14,2,1),
    )

    fun parts(dark: Boolean): List<Pair<Long, IntArray>> = if (dark) DARK else LIGHT
}
