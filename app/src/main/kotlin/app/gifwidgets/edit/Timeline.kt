package app.gifwidgets.edit

import kotlin.math.max
import kotlin.math.min

/** Result of [Timeline.plan]: which source frame to show for every output frame, at a constant fps. */
class TimelinePlan(val sourceIndices: List<Int>, val fps: Int, val restFrame: Int) {
    val frameCount: Int get() = sourceIndices.size
    val durationMs: Long get() = frameCount * 1000L / fps

    /** The distinct source frames that must be decoded. */
    val neededSources: Set<Int> get() = sourceIndices.toSet()
}

/** Pure maths: trim, delete, keep 1 of N, reverse, ping-pong and resampling to a constant fps. */
object Timeline {
    /** Same ceiling as the import limit: an optimized animation never has more frames than this. */
    const val MAX_OUTPUT_FRAMES = 400

    /**
     * [delaysCs] are the (already normalized) source delays in centiseconds.
     * If every frame is removed, the first frame of the trim range is kept so there is always one frame.
     */
    fun plan(delaysCs: IntArray, params: TimelineParams): TimelinePlan {
        require(delaysCs.isNotEmpty())
        val n = delaysCs.size
        var fps = params.fps.coerceIn(TimelineParams.MIN_FPS, TimelineParams.MAX_FPS)
        val start = params.trimStart.coerceIn(0, n - 1)
        val end = if (params.trimEnd < 0) n - 1 else params.trimEnd.coerceIn(start, n - 1)

        // (source index, duration in ms)
        var idx = ArrayList<Int>()
        var dur = ArrayList<Int>()
        for (i in start..end) {
            if (i in params.deleted) continue
            idx.add(i)
            dur.add(delaysCs[i] * 10)
        }
        if (idx.isEmpty()) {
            idx.add(start)
            dur.add(delaysCs[start] * 10)
        }

        val groupSize = params.keepOneOfN.coerceIn(1, 100)
        if (groupSize > 1) {
            val gi = ArrayList<Int>()
            val gd = ArrayList<Int>()
            var k = 0
            while (k < idx.size) {
                var sum = 0
                val last = min(k + groupSize, idx.size)
                for (j in k until last) sum += dur[j]
                gi.add(idx[k])
                gd.add(sum)
                k = last
            }
            idx = gi
            dur = gd
        }

        if (params.reverse) {
            idx.reverse()
            dur.reverse()
        }

        if (params.pingPong && idx.size > 1) {
            val back = idx.reversed().drop(1).dropLast(1)
            val backDur = dur.reversed().drop(1).dropLast(1)
            idx = ArrayList(idx + back)
            dur = ArrayList(dur + backDur)
        }

        // Resample to constant fps: output frame k shows the source frame playing at time k / fps
        // (the start of its slot, so the first output frame is always the first kept frame).
        var totalMs = 0L
        for (d in dur) totalMs += d
        var outCount = max(1, ((totalMs * fps + 500) / 1000).toInt())
        if (outCount > MAX_OUTPUT_FRAMES) {
            // Too many frames: lower the fps (keeps the duration) until it fits.
            fps = max(1, (MAX_OUTPUT_FRAMES * 1000L / totalMs).toInt())
            outCount = min(MAX_OUTPUT_FRAMES, max(1, ((totalMs * fps + 500) / 1000).toInt()))
        }
        val out = ArrayList<Int>(outCount)
        var j = 0
        var cumEnd = dur[0].toLong() // end of frame j, in ms
        for (k in 0 until outCount) {
            // time = k * 1000 / fps ms; compare scaled by fps to stay in integers
            val tScaled = k * 1000L
            while (j < idx.size - 1 && tScaled >= cumEnd * fps) {
                j++
                cumEnd += dur[j]
            }
            out.add(idx[j])
        }
        return TimelinePlan(out, fps, params.restFrame.coerceIn(0, outCount - 1))
    }

    /**
     * Output frame that best matches source frame [sourceIndex] (first output frame showing it,
     * otherwise the one showing the closest source frame). Used by "use the frame I am looking at as rest frame".
     */
    fun outputIndexFor(plan: TimelinePlan, sourceIndex: Int): Int {
        val exact = plan.sourceIndices.indexOf(sourceIndex)
        if (exact >= 0) return exact
        var best = 0
        var bestDist = Int.MAX_VALUE
        for ((k, src) in plan.sourceIndices.withIndex()) {
            val d = Math.abs(src - sourceIndex)
            if (d < bestDist) {
                bestDist = d
                best = k
            }
        }
        return best
    }
}
