package app.gifwidgets.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryZipTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"

    private fun tmp(): File = Files.createTempDirectory("lz").toFile()

    private fun library(): File {
        val dir = File(tmp(), id)
        File(dir, "frames").mkdirs()
        File(dir, "meta.json").writeText("""{"id":"$id"}""")
        File(dir, "original.gif").writeBytes(ByteArray(1000) { it.toByte() })
        File(dir, "frames/000.webp").writeBytes(ByteArray(300) { 1 })
        File(dir, "frames/001.webp").writeBytes(ByteArray(300) { 2 })
        File(dir, "thumbs").mkdirs()
        File(dir, "thumbs/t.png").writeBytes(ByteArray(10))
        return dir
    }

    private fun zipOf(vararg names: String): File {
        val f = File(tmp(), "x.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            for (n in names) {
                z.putNextEntry(ZipEntry(n))
                z.write(byteArrayOf(1, 2, 3))
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun roundTripKeepsFilesAndSkipsThumbnails() {
        val dir = library()
        val bytes = ByteArrayOutputStream()
        LibraryZipWriter.write(bytes, """{"format":1}""", listOf(ZipItem(id, dir)))
        val file = File(tmp(), "lib.zip").also { it.writeBytes(bytes.toByteArray()) }
        ZipFile(file).use { zip ->
            val scan = LibraryZipReader.scan(zip)
            assertEquals(setOf(id), scan.entriesById.keys)
            assertEquals("""{"format":1}""", LibraryZipReader.readManifest(zip))
            assertEquals(4, scan.entriesById[id]!!.size) // meta, original, 2 frames (no thumbs)
            val dest = tmp()
            LibraryZipReader.extract(zip, scan.entriesById[id]!!, dest, LibraryZipReader.Budget(10_000))
            assertEquals(1000L, File(dest, "original.gif").length())
            assertTrue(File(dest, "frames/001.webp").readBytes().all { it == 2.toByte() })
            assertFalse(File(dest, "thumbs").exists())
        }
    }

    @Test
    fun zipSlipAndOddNamesAreRejected() {
        val bad = listOf(
            "../evil.txt", "/etc/passwd", "animations/$id/../../x", "animations\\$id\\meta.json",
            "animations/$id/frames/../meta.json", "C:/x", "animations/$id/meta.json/", "animations/not-a-uuid/meta.json",
            "animations/$id/other.bin", "animations//meta.json", "./manifest.json", "",
        )
        for (n in bad) assertNull("should reject '$n'", ZipPaths.classify(n))
        assertNotNull(ZipPaths.classify("manifest.json"))
        assertNotNull(ZipPaths.classify("animations/$id/meta.json"))
        assertNotNull(ZipPaths.classify("animations/$id/original.gif"))
        assertNotNull(ZipPaths.classify("animations/$id/frames/000.webp"))
    }

    @Test
    fun scanThrowsOnHostileOrIncompleteZips() {
        fun rejects(f: File, limits: ZipLimits = ZipLimits()): Boolean =
            try { ZipFile(f).use { LibraryZipReader.scan(it, limits) }; false } catch (e: ZipFormatException) { true }
        assertTrue(rejects(zipOf("manifest.json", "../evil")))
        assertTrue(rejects(zipOf("animations/$id/meta.json")))                  // no manifest
        assertFalse(rejects(zipOf("manifest.json", "animations/$id/meta.json")))
        assertTrue(rejects(zipOf("manifest.json", "animations/$id/meta.json"), ZipLimits(maxEntries = 1)))
        assertTrue(rejects(zipOf("manifest.json", "animations/$id/meta.json"), ZipLimits(maxEntryBytes = 2)))
    }

    @Test
    fun extractStopsAtTheLimitsEvenIfHeadersLie() {
        val dir = library()
        val bytes = ByteArrayOutputStream()
        LibraryZipWriter.write(bytes, "{}", listOf(ZipItem(id, dir)))
        val file = File(tmp(), "lib.zip").also { it.writeBytes(bytes.toByteArray()) }
        ZipFile(file).use { zip ->
            val entries = LibraryZipReader.scan(zip).entriesById[id]!!
            var failed = false
            try {
                LibraryZipReader.extract(zip, entries, tmp(), LibraryZipReader.Budget(500))
            } catch (e: ZipFormatException) {
                failed = true
            }
            assertTrue(failed)
            failed = false
            try {
                LibraryZipReader.extract(zip, entries, tmp(), LibraryZipReader.Budget(10_000), ZipLimits(maxEntryBytes = 100))
            } catch (e: ZipFormatException) {
                failed = true
            }
            assertTrue(failed)
        }
    }
}
