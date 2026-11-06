package app.gifwidgets.gif

/**
 * Pure-Kotlin (no android.* imports) GIF decoder, written from the public
 * GIF89a specification. It is NOT copied from or derived from the Xcellent
 * Creations GifDecoder gist, so no third-party licence applies to it
 * (see THIRD_PARTY.md).
 *
 * How it works:
 *  1. [open] scans the file structure once, WITHOUT decoding pixels, to learn
 *     the size, frame count, delays and transparency, and to enforce
 *     [GifLimits]. Frame pixels are never all in memory.
 *  2. [decode] then decodes frames one at a time, in order, composing them
 *     (with disposal methods) on one reusable canvas.
 *
 * Canvas pixels are ARGB ints (0xAARRGGBB); 0 means fully transparent.
 */
class GifInfo(
    val width: Int,
    val height: Int,
    val delaysCs: IntArray,
    /** NETSCAPE loop count: 0 = forever, N = N extra repeats, -1 = no extension (play once). */
    val loopCount: Int,
    /** True if any frame declares a transparent colour index. */
    val usesTransparency: Boolean,
) {
    val frameCount: Int get() = delaysCs.size
    val totalDurationCs: Long get() = delaysCs.fold(0L) { a, b -> a + b }
    val averageFps: Int get() = GifTiming.averageFps(delaysCs)
}

class GifReader private constructor(
    private val data: ByteArray,
    private val limits: GifLimits,
    private val gctOffset: Int,
    private val gctCount: Int,
    private val frames: List<FrameRef>,
    val info: GifInfo,
) {

    private class FrameRef(
        val delayCs: Int,
        val disposal: Int,
        val hasTransparency: Boolean,
        val transparentIndex: Int,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val interlaced: Boolean,
        val lctOffset: Int, // -1 when the frame uses the global colour table
        val lctCount: Int,
        val minCodeSize: Int,
        val dataStart: Int, // offset of the first data sub-block length byte
    )

    /**
     * Decodes frames in order. [onFrame] receives the frame index, its
     * normalized delay (cs) and the composed canvas. The canvas array is REUSED:
     * copy or consume it before returning. Return false to stop early.
     */
    fun decode(onFrame: (index: Int, delayCs: Int, canvas: IntArray) -> Boolean) {
        val w = info.width
        val h = info.height
        val canvas = IntArray(w * h)
        var saved: IntArray? = null
        var prev: FrameRef? = null
        var indices = ByteArray(0)
        val palette = IntArray(256)

        for ((i, f) in frames.withIndex()) {
            // 1. Dispose the previous frame.
            val p = prev
            if (p != null) {
                when (p.disposal) {
                    2 -> clearRect(canvas, w, h, p)
                    3 -> saved?.copyInto(canvas)
                }
            }
            // 2. Snapshot for "restore to previous".
            if (f.disposal == 3) {
                val s = saved ?: IntArray(canvas.size).also { saved = it }
                canvas.copyInto(s)
            }
            // 3. Draw.
            val npix = f.width * f.height
            if (npix > 0) {
                if (indices.size < npix) indices = ByteArray(npix)
                val decoded = lzwDecode(f.dataStart, f.minCodeSize, npix, indices)
                loadPalette(f, palette)
                drawFrame(canvas, w, h, f, indices, decoded, palette)
            }
            if (!onFrame(i, GifTiming.normalizeDelayCs(f.delayCs), canvas)) return
            prev = f
        }
    }

    private fun clearRect(canvas: IntArray, w: Int, h: Int, f: FrameRef) {
        val x0 = f.left.coerceAtLeast(0)
        val y0 = f.top.coerceAtLeast(0)
        val x1 = (f.left + f.width).coerceAtMost(w)
        val y1 = (f.top + f.height).coerceAtMost(h)
        for (y in y0 until y1) {
            val row = y * w
            for (x in x0 until x1) canvas[row + x] = 0
        }
    }

    private fun loadPalette(f: FrameRef, palette: IntArray) {
        val offset: Int
        val count: Int
        if (f.lctOffset >= 0) {
            offset = f.lctOffset
            count = f.lctCount
        } else if (gctOffset >= 0) {
            offset = gctOffset
            count = gctCount
        } else {
            throw GifException(GifException.Reason.CORRUPT, "Frame without a colour table")
        }
        for (c in 0 until 256) {
            if (c < count) {
                val o = offset + c * 3
                val r = data[o].toInt() and 0xFF
                val g = data[o + 1].toInt() and 0xFF
                val b = data[o + 2].toInt() and 0xFF
                palette[c] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            } else {
                palette[c] = 0xFF000000.toInt() // out-of-table index: opaque black
            }
        }
    }

    private fun drawFrame(
        canvas: IntArray, w: Int, h: Int, f: FrameRef,
        indices: ByteArray, decoded: Int, palette: IntArray,
    ) {
        for (r in 0 until f.height) {
            val destRow = if (f.interlaced) interlacedRow(r, f.height) else r
            val y = f.top + destRow
            if (y < 0 || y >= h) continue
            val srcRow = r * f.width
            for (c in 0 until f.width) {
                val src = srcRow + c
                if (src >= decoded) return // truncated data: keep what we have
                val x = f.left + c
                if (x < 0 || x >= w) continue
                val idx = indices[src].toInt() and 0xFF
                if (f.hasTransparency && idx == f.transparentIndex) continue
                canvas[y * w + x] = palette[idx]
            }
        }
    }

    /** Reads the frame's data sub-blocks and LZW-decodes them into [out]. Returns pixels produced. */
    private fun lzwDecode(dataStart: Int, minCodeSize: Int, npix: Int, out: ByteArray): Int {
        // Join sub-blocks.
        var total = 0
        var pos = dataStart
        while (pos < data.size) {
            val n = data[pos].toInt() and 0xFF
            if (n == 0) break
            val avail = minOf(n, data.size - pos - 1)
            total += avail
            pos += 1 + n
        }
        val src = ByteArray(total)
        var w = 0
        pos = dataStart
        while (pos < data.size && w < total) {
            val n = data[pos].toInt() and 0xFF
            if (n == 0) break
            val avail = minOf(n, data.size - pos - 1)
            System.arraycopy(data, pos + 1, src, w, avail)
            w += avail
            pos += 1 + n
        }

        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val prefix = ShortArray(4096)
        val suffix = ByteArray(4096)
        val stack = ByteArray(4097)
        for (i in 0 until clear) suffix[i] = i.toByte()

        var avail = clear + 2
        var codeSize = minCodeSize + 1
        var codeMask = (1 shl codeSize) - 1
        var oldCode = -1
        var first = 0
        var bits = 0
        var datum = 0
        var bytePos = 0
        var top = 0
        var produced = 0

        while (produced < npix) {
            if (top == 0) {
                while (bits < codeSize && bytePos < src.size) {
                    datum = datum or ((src[bytePos++].toInt() and 0xFF) shl bits)
                    bits += 8
                }
                if (bits < codeSize) break // ran out of data
                val code = datum and codeMask
                datum = datum ushr codeSize
                bits -= codeSize

                if (code > avail || code == eoi) break
                if (code == clear) {
                    codeSize = minCodeSize + 1
                    codeMask = (1 shl codeSize) - 1
                    avail = clear + 2
                    oldCode = -1
                    continue
                }
                if (oldCode == -1) {
                    if (code >= clear) break // first code after a clear must be a literal: corrupt
                    stack[top++] = suffix[code]
                    oldCode = code
                    first = code
                } else {
                    val inCode = code
                    var c = code
                    if (c >= avail) { // KwKwK case
                        stack[top++] = first.toByte()
                        c = oldCode
                    }
                    while (c >= clear) {
                        if (top >= 4096) return produced // corrupt prefix chain: stop, keep what we have
                        stack[top++] = suffix[c]
                        c = prefix[c].toInt() and 0xFFFF
                    }
                    first = suffix[c].toInt() and 0xFF
                    stack[top++] = first.toByte()
                    if (avail < 4096) {
                        prefix[avail] = oldCode.toShort()
                        suffix[avail] = first.toByte()
                        avail++
                        if ((avail and codeMask) == 0 && avail < 4096) {
                            codeSize++
                            codeMask += avail
                        }
                    }
                    oldCode = inCode
                }
            }
            top--
            out[produced++] = stack[top]
        }
        return produced
    }

    companion object {
        private const val SIG_LEN = 6

        /** True if [head] starts with "GIF87a" or "GIF89a". Does not trust any MIME type. */
        fun hasGifSignature(head: ByteArray, length: Int = head.size): Boolean {
            if (length < SIG_LEN) return false
            val s = String(head, 0, SIG_LEN, Charsets.ISO_8859_1)
            return s == "GIF87a" || s == "GIF89a"
        }

        /** Row (0-based, top to bottom) where the [r]-th stored row of an interlaced image belongs. */
        fun interlacedRow(r: Int, height: Int): Int {
            val p1 = (height + 7) / 8
            val p2 = (height + 3) / 8
            val p3 = (height + 1) / 4
            return when {
                r < p1 -> r * 8
                r < p1 + p2 -> (r - p1) * 8 + 4
                r < p1 + p2 + p3 -> (r - p1 - p2) * 4 + 2
                else -> (r - p1 - p2 - p3) * 2 + 1
            }
        }

        private fun truncated(): Nothing =
            throw GifException(GifException.Reason.TRUNCATED, "Unexpected end of GIF data")

        /** Scans the GIF structure and enforces [limits]. Throws [GifException] if it must be rejected. */
        fun open(data: ByteArray, limits: GifLimits = GifLimits()): GifReader {
            if (data.size.toLong() > limits.maxFileBytes) {
                throw GifException(GifException.Reason.FILE_TOO_LARGE, "File is ${data.size} bytes")
            }
            if (!hasGifSignature(data)) {
                throw GifException(GifException.Reason.NOT_GIF, "Missing GIF87a/GIF89a signature")
            }
            val cur = Cursor(data)
            cur.pos = SIG_LEN
            val width = cur.u16()
            val height = cur.u16()
            val packed = cur.u8()
            cur.u8() // background colour index (unused: background is transparent)
            cur.u8() // pixel aspect ratio (ignored)

            if (width <= 0 || height <= 0) {
                throw GifException(GifException.Reason.CORRUPT, "Zero-sized canvas")
            }
            if (width > limits.maxSide || height > limits.maxSide ||
                width.toLong() * height > limits.maxCanvasPixels
            ) {
                throw GifException(GifException.Reason.DIMENSIONS_TOO_BIG, "Canvas ${width}x$height")
            }

            var gctOffset = -1
            var gctCount = 0
            if (packed and 0x80 != 0) {
                gctCount = 1 shl ((packed and 0x07) + 1)
                gctOffset = cur.pos
                cur.skip(3 * gctCount)
            }

            val frames = ArrayList<FrameRef>()
            var loopCount = -1
            var usesTransparency = false

            // Pending graphic control extension for the next image.
            var gDelay = 0
            var gDisposal = 0
            var gTransFlag = false
            var gTransIdx = 0

            try {
                loop@ while (true) {
                    when (cur.u8()) {
                        0x21 -> { // extension
                            val label = cur.u8()
                            if (label == 0xF9) {
                                val size = cur.u8()
                                if (size >= 4) {
                                    val gp = cur.u8()
                                    gDelay = cur.u16()
                                    gTransIdx = cur.u8()
                                    cur.skip(size - 4)
                                    gDisposal = (gp shr 2) and 0x07
                                    gTransFlag = (gp and 0x01) != 0
                                } else {
                                    cur.skip(size)
                                }
                                cur.skipSubBlocks()
                            } else if (label == 0xFF) {
                                val size = cur.u8()
                                val idStart = cur.pos
                                cur.skip(size)
                                val isLoopExt = size == 11 && matches(data, idStart, "NETSCAPE2.0")
                                if (isLoopExt) {
                                    while (true) {
                                        val n = cur.u8()
                                        if (n == 0) break
                                        if (n >= 3) {
                                            val id = cur.u8()
                                            val lc = cur.u16()
                                            cur.skip(n - 3)
                                            if (id == 1) loopCount = lc
                                        } else {
                                            cur.skip(n)
                                        }
                                    }
                                } else {
                                    cur.skipSubBlocks()
                                }
                            } else {
                                cur.skipSubBlocks()
                            }
                        }
                        0x2C -> { // image descriptor
                            val left = cur.u16()
                            val top = cur.u16()
                            val fw = cur.u16()
                            val fh = cur.u16()
                            val ip = cur.u8()
                            var lctOffset = -1
                            var lctCount = 0
                            if (ip and 0x80 != 0) {
                                lctCount = 1 shl ((ip and 0x07) + 1)
                                lctOffset = cur.pos
                                cur.skip(3 * lctCount)
                            }
                            val minCode = cur.u8()
                            if (minCode !in 1..8) {
                                throw GifException(GifException.Reason.CORRUPT, "LZW code size $minCode")
                            }
                            if (fw.toLong() * fh > limits.maxCanvasPixels) {
                                throw GifException(GifException.Reason.CORRUPT, "Frame ${fw}x$fh too big")
                            }
                            val dataStart = cur.pos
                            cur.skipSubBlocks()

                            frames.add(
                                FrameRef(
                                    delayCs = gDelay,
                                    disposal = if (gDisposal in 2..3) gDisposal else 0,
                                    hasTransparency = gTransFlag,
                                    transparentIndex = gTransIdx,
                                    left = left, top = top, width = fw, height = fh,
                                    interlaced = (ip and 0x40) != 0,
                                    lctOffset = lctOffset, lctCount = lctCount,
                                    minCodeSize = minCode, dataStart = dataStart,
                                ),
                            )
                            if (gTransFlag) usesTransparency = true
                            if (frames.size > limits.maxFrames) {
                                throw GifException(
                                    GifException.Reason.TOO_MANY_FRAMES,
                                    "More than ${limits.maxFrames} frames",
                                )
                            }
                            // The graphic control extension only applies to the next image.
                            gDelay = 0; gDisposal = 0; gTransFlag = false; gTransIdx = 0
                        }
                        0x3B -> break@loop // trailer
                        else -> {
                            if (frames.isEmpty()) {
                                throw GifException(GifException.Reason.CORRUPT, "Unknown block")
                            }
                            break@loop // garbage after the last frame: keep what we have
                        }
                    }
                }
            } catch (e: GifException) {
                // A file cut short after at least one complete frame is still usable.
                if (e.reason == GifException.Reason.TRUNCATED && frames.isNotEmpty()) {
                    // keep decoded structure so far
                } else {
                    throw e
                }
            }

            if (frames.isEmpty()) {
                throw GifException(GifException.Reason.NO_FRAMES, "GIF has no frames")
            }

            val delays = IntArray(frames.size) { GifTiming.normalizeDelayCs(frames[it].delayCs) }
            val info = GifInfo(width, height, delays, loopCount, usesTransparency)
            return GifReader(data, limits, gctOffset, gctCount, frames, info)
        }

        private fun matches(data: ByteArray, offset: Int, text: String): Boolean {
            if (offset + text.length > data.size) return false
            for (i in text.indices) if (data[offset + i].toInt() != text[i].code) return false
            return true
        }
    }

    /** Bounds-checked byte reader. Throws TRUNCATED instead of ArrayIndexOutOfBounds. */
    private class Cursor(private val d: ByteArray) {
        var pos = 0
        fun u8(): Int {
            if (pos >= d.size) truncated()
            return d[pos++].toInt() and 0xFF
        }
        fun u16(): Int {
            val a = u8()
            val b = u8()
            return a or (b shl 8)
        }
        fun skip(n: Int) {
            if (n < 0 || pos.toLong() + n > d.size) truncated()
            pos += n
        }
        fun skipSubBlocks() {
            while (true) {
                val n = u8()
                if (n == 0) return
                skip(n)
            }
        }
    }
}
