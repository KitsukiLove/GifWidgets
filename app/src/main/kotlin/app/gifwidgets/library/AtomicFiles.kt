package app.gifwidgets.library

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Write to a temp file, then rename, so a crash never leaves a half-written file. */
object AtomicFiles {
    /**
     * The temp file has a name of its own for every call: two threads saving the same target at the same time
     * (for example a quick double tap on a widget) must not share one ".tmp" file, or the second rename fails.
     */
    fun writeBytes(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + "." + UUID.randomUUID() + ".tmp")
        try {
            tmp.writeBytes(bytes)
            moveInto(tmp, target)
        } finally {
            tmp.delete() // no-op after a successful move; cleans up after a failure
        }
    }

    fun moveInto(source: File, target: File) {
        try {
            Files.move(
                source.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
