package app.gifwidgets.gif

import kotlin.math.max

/**
 * A rough guess of what kind of picture a GIF is, from its original size alone (pure, no Android).
 *
 * Small GIFs (pixel art, stickers, icons) must stay crisp: they are enlarged with nearest-neighbour and, when the
 * widget memory runs short, frames are dropped before sharpness is. Big GIFs (photos, banners, screen
 * recordings) look better smooth: they are scaled with an area average / bilinear filter, and when the memory runs
 * short they keep every frame and lose a little resolution instead.
 */
object GifKind {
    /** Longest side, in pixels, up to which a GIF counts as pixel art. */
    const val PIXEL_ART_MAX_SIDE = 256

    fun isPixelArt(srcWidth: Int, srcHeight: Int): Boolean = max(srcWidth, srcHeight) in 1..PIXEL_ART_MAX_SIDE
}
