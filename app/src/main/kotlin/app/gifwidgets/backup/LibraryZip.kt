package app.gifwidgets.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Limits applied to every ZIP we import (the file may be corrupt or hostile). */
data class ZipLimits(
    val maxEntries: Int = 30_000,
    val maxEntryBytes: Long = 64L * 1024 * 1024,
    val maxTotalBytes: Long = 1024L * 1024 * 1024,
    val maxManifestBytes: Long = 1L * 1024 * 1024,
)

/** The ZIP is not a valid GifWidgets library (or breaks a limit). The message is for logs, not for the user. */
class ZipFormatException(message: String) : IOException(message)

/**
 * Layout of a library ZIP (format 1):
 *   manifest.json
 *   animations/<uuid>/meta.json
 *   animations/<uuid>/original.gif        (optional)
 *   animations/<uuid>/frames/000.webp ... (optional)
 * Anything else is rejected, which also rules out "zip slip" (names with "..", absolute paths, backslashes).
 */
object ZipPaths {
    const val MANIFEST = "manifest.json"
    const val META = "meta.json"
    const val ORIGINAL = "original.gif"
    const val FRAMES = "frames"

    enum class Kind { MANIFEST, META, ORIGINAL, FRAME }

    class Parsed(val kind: Kind, val id: String?, val file: String)

    private const val UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val ENTRY = Regex("animations/($UUID)/(meta\\.json|original\\.gif|frames/[0-9]{3,5}\\.webp)")

    /** True when [name] cannot escape the folder it is extracted into. */
    fun isSafe(name: String): Boolean {
        if (name.isEmpty() || name.length > 200) return false
        if (name.startsWith("/") || name.contains('\\') || name.contains('\u0000') || name.contains(':')) return false
        return name.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    /** Null = not an entry we accept. Directory entries are not accepted either (we create folders ourselves). */
    fun classify(name: String): Parsed? {
        if (!isSafe(name)) return null
        if (name == MANIFEST) return Parsed(Kind.MANIFEST, null, MANIFEST)
        val m = ENTRY.matchEntire(name) ?: return null
        val file = m.groupValues[2]
        val kind = when {
            file == META -> Kind.META
            file == ORIGINAL -> Kind.ORIGINAL
            else -> Kind.FRAME
        }
        return Parsed(kind, m.groupValues[1], file)
    }
}

/** One animation folder to export. */
class ZipItem(val id: String, val dir: File)

object LibraryZipWriter {

    /** Entries are STORED (WebP and GIF are already compressed) except the JSON. */
    fun write(out: OutputStream, manifestJson: String, items: List<ZipItem>) {
        val zip = ZipOutputStream(out.buffered())
        putText(zip, ZipPaths.MANIFEST, manifestJson)
        for (item in items) {
            val base = "animations/${item.id}/"
            val meta = File(item.dir, ZipPaths.META)
            if (!meta.isFile) throw IOException("meta.json missing for ${item.id}")
            putText(zip, base + ZipPaths.META, meta.readText(Charsets.UTF_8))
            val original = File(item.dir, ZipPaths.ORIGINAL)
            if (original.isFile) putStored(zip, base + ZipPaths.ORIGINAL, original)
            val frames = File(item.dir, ZipPaths.FRAMES).listFiles { f -> f.isFile && f.name.endsWith(".webp") }
                ?.sortedBy { it.name }.orEmpty()
            for (f in frames) putStored(zip, "$base${ZipPaths.FRAMES}/${f.name}", f)
        }
        zip.finish()
        zip.flush()
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putStored(zip: ZipOutputStream, name: String, file: File) {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        val e = ZipEntry(name)
        e.method = ZipEntry.STORED
        e.size = file.length()
        e.compressedSize = file.length()
        e.crc = crc.value
        zip.putNextEntry(e)
        file.inputStream().use { it.copyTo(zip, 32 * 1024) }
        zip.closeEntry()
    }
}

/** What a scan found: the entries of each animation, all already validated. */
class ZipScan(val entriesById: Map<String, List<Pair<ZipEntry, ZipPaths.Parsed>>>)

object LibraryZipReader {

    /** Checks every entry name and the entry count; returns the entries grouped by animation id. */
    fun scan(zip: ZipFile, limits: ZipLimits = ZipLimits()): ZipScan {
        val byId = LinkedHashMap<String, MutableList<Pair<ZipEntry, ZipPaths.Parsed>>>()
        var count = 0
        var manifest = false
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            if (++count > limits.maxEntries) throw ZipFormatException("too many entries")
            val parsed = ZipPaths.classify(e.name) ?: throw ZipFormatException("unexpected entry: ${e.name.take(80)}")
            if (e.isDirectory) throw ZipFormatException("directory entry")
            if (e.size > limits.maxEntryBytes) throw ZipFormatException("entry too large")
            if (parsed.kind == ZipPaths.Kind.MANIFEST) {
                if (manifest) throw ZipFormatException("duplicate manifest")
                manifest = true
            } else {
                val list = byId.getOrPut(parsed.id!!) { ArrayList() }
                if (list.any { it.second.file == parsed.file }) throw ZipFormatException("duplicate entry")
                list.add(e to parsed)
            }
        }
        if (!manifest) throw ZipFormatException("manifest.json missing")
        return ZipScan(byId)
    }

    fun readManifest(zip: ZipFile, limits: ZipLimits = ZipLimits()): String {
        val e = zip.getEntry(ZipPaths.MANIFEST) ?: throw ZipFormatException("manifest.json missing")
        return String(readLimited(zip.getInputStream(e), limits.maxManifestBytes), Charsets.UTF_8)
    }

    fun readText(zip: ZipFile, entry: ZipEntry, maxBytes: Long): String =
        String(readLimited(zip.getInputStream(entry), maxBytes), Charsets.UTF_8)

    /** Counts bytes as they are copied (the sizes written in a ZIP header can lie). */
    class Budget(var remainingTotal: Long)

    /**
     * Copies every entry of one animation into [destDir] (created if needed). Bytes are counted while copying,
     * against both the per-entry limit and the shared [budget].
     */
    fun extract(
        zip: ZipFile,
        entries: List<Pair<ZipEntry, ZipPaths.Parsed>>,
        destDir: File,
        budget: Budget,
        limits: ZipLimits = ZipLimits(),
    ) {
        for ((entry, parsed) in entries) {
            val target = File(destDir, parsed.file) // parsed.file passed isSafe: only meta.json, original.gif, frames/NNN.webp
            target.parentFile?.mkdirs()
            var written = 0L
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        budget.remainingTotal -= n
                        if (written > limits.maxEntryBytes || budget.remainingTotal < 0) {
                            throw ZipFormatException("size limit exceeded")
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
        }
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        input.use {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var total = 0L
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                total += n
                if (total > max) throw ZipFormatException("entry too large")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
