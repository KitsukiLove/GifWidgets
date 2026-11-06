package app.gifwidgets.library

import app.gifwidgets.gif.GifException
import java.io.InputStream
import java.io.OutputStream

/** Copies a stream but refuses to go past [maxBytes] (hostile or huge files). Pure java.io. */
object CappedCopy {
    fun copy(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buf = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) {
                throw GifException(GifException.Reason.FILE_TOO_LARGE, "More than $maxBytes bytes")
            }
            output.write(buf, 0, n)
        }
        return total
    }
}
