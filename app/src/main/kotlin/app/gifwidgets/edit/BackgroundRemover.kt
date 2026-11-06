package app.gifwidgets.edit

/** Pure background removal. No AI: only colour maths and flood fill. Works in place on ARGB ints. */
object BackgroundRemover {

    private const val MAX_REDMEAN_DISTANCE = 765.0

    /** "Redmean" weighted colour distance, 0 (same) to about 765 (black vs white). Alpha is ignored. */
    fun distanceSq(a: Int, b: Int): Double {
        val r1 = (a shr 16) and 0xFF
        val g1 = (a shr 8) and 0xFF
        val b1 = a and 0xFF
        val r2 = (b shr 16) and 0xFF
        val g2 = (b shr 8) and 0xFF
        val b2 = b and 0xFF
        val rmean = (r1 + r2) / 2.0
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()
        return (2.0 + rmean / 256.0) * dr * dr + 4.0 * dg * dg + (2.0 + (255.0 - rmean) / 256.0) * db * db
    }

    fun apply(px: IntArray, w: Int, h: Int, params: BackgroundParams) {
        require(px.size >= w * h)
        val p = params.sanitized()
        when (p.mode) {
            BackgroundMode.KEEP -> Unit
            BackgroundMode.COLOR_KEY -> colorKey(px, w, h, p)
            BackgroundMode.BLACK_TO_ALPHA -> blackToAlpha(px, w * h, p)
        }
    }

    // ---- colour key ----

    private fun colorKey(px: IntArray, w: Int, h: Int, p: BackgroundParams) {
        val n = w * h
        val matchAll = p.tolerance >= 100
        val limit = p.tolerance / 100.0 * MAX_REDMEAN_DISTANCE
        val limitSq = limit * limit
        val key = p.keyColor

        fun matches(i: Int): Boolean =
            matchAll || (px[i] ushr 24) == 0 || distanceSq(px[i], key) <= limitSq

        val removed = BooleanArray(n)
        if (p.contiguous) {
            val queue = IntArray(n)
            var head = 0
            var tail = 0
            fun seed(i: Int) {
                if (!removed[i] && matches(i)) {
                    removed[i] = true
                    queue[tail++] = i
                }
            }
            for (x in 0 until w) {
                seed(x)
                seed((h - 1) * w + x)
            }
            for (y in 0 until h) {
                seed(y * w)
                seed(y * w + w - 1)
            }
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (x > 0) seed(i - 1)
                if (x < w - 1) seed(i + 1)
                if (y > 0) seed(i - w)
                if (y < h - 1) seed(i + w)
            }
        } else {
            for (i in 0 until n) removed[i] = matches(i)
        }

        val depth = maxOf(p.edgeSoften, if (p.defringe) 1 else 0)
        if (depth > 0) {
            val dist = distanceFromRemoved(removed, w, h, depth)
            if (p.defringe) defringe(px, removed, dist, w, h)
            if (p.edgeSoften > 0) {
                val n1 = p.edgeSoften + 1
                for (i in 0 until n) {
                    val d = dist[i]
                    if (!removed[i] && d in 1..p.edgeSoften) {
                        val a = px[i] ushr 24
                        val newA = (a * d + n1 / 2) / n1
                        px[i] = (newA shl 24) or (px[i] and 0x00FFFFFF)
                    }
                }
            }
        }
        for (i in 0 until n) if (removed[i]) px[i] = 0
    }

    /** Chebyshev distance (8 neighbours) from the removed region, up to [maxDepth]; further = Int.MAX_VALUE. */
    private fun distanceFromRemoved(removed: BooleanArray, w: Int, h: Int, maxDepth: Int): IntArray {
        val n = w * h
        val dist = IntArray(n) { if (removed[it]) 0 else Int.MAX_VALUE }
        var frontier = IntArray(n)
        var next = IntArray(n)
        var fs = 0
        for (i in 0 until n) if (removed[i]) frontier[fs++] = i
        for (depth in 1..maxDepth) {
            var ns = 0
            for (k in 0 until fs) {
                val i = frontier[k]
                val x = i % w
                val y = i / w
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        val j = yy * w + xx
                        if (dist[j] == Int.MAX_VALUE) {
                            dist[j] = depth
                            next[ns++] = j
                        }
                    }
                }
            }
            val t = frontier; frontier = next; next = t
            fs = ns
        }
        return dist
    }

    /** Edge pixels (distance 1) take the average colour of their kept neighbours that are further inside. */
    private fun defringe(px: IntArray, removed: BooleanArray, dist: IntArray, w: Int, h: Int) {
        val out = px.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (removed[i] || dist[i] != 1) continue
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        val j = yy * w + xx
                        if (!removed[j] && dist[j] >= 2) {
                            val c = px[j]
                            r += (c shr 16) and 0xFF
                            g += (c shr 8) and 0xFF
                            b += c and 0xFF
                            count++
                        }
                    }
                }
                if (count > 0) {
                    out[i] = (px[i] and 0xFF000000.toInt()) or
                        (((r + count / 2) / count) shl 16) or
                        (((g + count / 2) / count) shl 8) or
                        ((b + count / 2) / count)
                }
            }
        }
        System.arraycopy(out, 0, px, 0, px.size.coerceAtMost(out.size))
    }

    // ---- black to transparent ----

    private fun blackToAlpha(px: IntArray, n: Int, p: BackgroundParams) {
        val t = p.blackThreshold
        for (i in 0 until n) {
            val c = px[i]
            val srcA = c ushr 24
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val m = maxOf(r, g, b)
            if (m == 0 || srcA == 0) {
                px[i] = 0
                continue
            }
            var a = if (m <= t) 0 else (m - t) * 255 / (255 - t)
            a = minOf(255, a * p.blackGain / 100)
            val outA = (a * srcA + 127) / 255
            if (outA == 0) {
                px[i] = 0
                continue
            }
            val rr = minOf(255, (r * 255 + m / 2) / m)
            val gg = minOf(255, (g * 255 + m / 2) / m)
            val bb = minOf(255, (b * 255 + m / 2) / m)
            px[i] = (outA shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }
}
