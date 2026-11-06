package app.gifwidgets.library

import app.gifwidgets.gif.GifException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class LibraryHelpersTest {

    @Test
    fun cappedCopyCopiesEverythingUnderTheLimit() {
        val src = ByteArray(100_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        val n = CappedCopy.copy(ByteArrayInputStream(src), out, 100_000)
        assertEquals(100_000L, n)
        assertArrayEquals(src, out.toByteArray())
    }

    @Test
    fun cappedCopyStopsOneByteOverTheLimit() {
        val src = ByteArray(1001)
        try {
            CappedCopy.copy(ByteArrayInputStream(src), ByteArrayOutputStream(), 1000)
            fail("Expected FILE_TOO_LARGE")
        } catch (e: GifException) {
            assertEquals(GifException.Reason.FILE_TOO_LARGE, e.reason)
        }
    }

    @Test
    fun namesAreSanitized() {
        assertEquals("KitsukiLove", AnimationNames.sanitize("KitsukiLove.GIF", "x"))
        assertEquals("a b", AnimationNames.sanitize("a/b.gif", "x"))
        assertEquals("a b c", AnimationNames.sanitize("  a \n\t b   c ", "x"))
        assertEquals("x", AnimationNames.sanitize(null, "x"))
        assertEquals("x", AnimationNames.sanitize("   .gif", "x"))
        assertEquals(AnimationNames.MAX_LENGTH, AnimationNames.sanitize("n".repeat(500), "x").length)
    }

    @Test
    fun atomicWritesFromTwoThreadsNeverFailAndLeaveNoTempFiles() {
        // Two quick taps on a widget save the same settings file at the same time.
        val dir = Files.createTempDirectory("gifwidgets-race").toFile()
        try {
            val target = java.io.File(dir, "settings.json")
            val failures = java.util.concurrent.atomic.AtomicInteger(0)
            val work = Runnable {
                repeat(300) { i ->
                    try {
                        AtomicFiles.writeBytes(target, "{\"v\":$i}".toByteArray())
                    } catch (e: java.io.IOException) {
                        failures.incrementAndGet()
                    }
                }
            }
            val threads = List(2) { Thread(work) }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            assertEquals(0, failures.get())
            assertEquals(listOf("settings.json"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun atomicWriteCreatesAndReplacesWithoutLeavingTempFiles() {
        val dir = Files.createTempDirectory("gifwidgets-test").toFile()
        try {
            val target = java.io.File(dir, "meta.json")
            AtomicFiles.writeBytes(target, "one".toByteArray())
            AtomicFiles.writeBytes(target, "two".toByteArray())
            assertEquals("two", target.readText())
            assertEquals(listOf("meta.json"), dir.list()!!.toList())
            assertFalse(java.io.File(dir, "meta.json.tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
        assertTrue(true)
    }
}
