package app.gifwidgets.library

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

/** The animation library: filesDir/library/<id>/{original.gif, meta.json, ...}. App-private storage only. */
class LibraryStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "library")

    fun dirFor(id: String): File {
        require(AnimationMeta.isValidId(id)) { "Bad animation id" }
        return File(root, id)
    }

    /** Newest first. Folders with missing or broken meta.json are skipped, not deleted. */
    fun list(): List<AnimationMeta> {
        val dirs = root.listFiles() ?: return emptyList()
        return dirs.filter { it.isDirectory }
            .mapNotNull { readMeta(it) }
            .sortedByDescending { it.createdAt }
    }

    fun writeMeta(meta: AnimationMeta) {
        AtomicFiles.writeBytes(
            File(dirFor(meta.id), META_FILE),
            meta.toJson().toByteArray(Charsets.UTF_8),
        )
    }

    /** Renames an animation. Blank or unusable names keep the old name. Returns the new meta, or null if not found. */
    fun rename(id: String, newName: String): AnimationMeta? {
        val old = readMeta(dirFor(id)) ?: return null
        val updated = old.copy(name = AnimationNames.sanitize(newName, old.name))
        writeMeta(updated)
        return updated
    }

    fun setFavorite(id: String, favorite: Boolean): AnimationMeta? {
        val old = readMeta(dirFor(id)) ?: return null
        val updated = old.copy(favorite = favorite)
        writeMeta(updated)
        return updated
    }

    /** Puts an animation in a collection (blank = takes it out of any). Returns the new meta, or null if not found. */
    fun setCollection(id: String, collection: String): AnimationMeta? {
        val old = readMeta(dirFor(id)) ?: return null
        val updated = old.copy(collection = LibraryOrganizer.cleanCollection(collection))
        writeMeta(updated)
        return updated
    }

    fun framesDir(id: String): File = File(dirFor(id), FRAMES_DIR)

    fun hqFramesDir(id: String): File = File(dirFor(id), HQ_FRAMES_DIR)

    fun exists(id: String): Boolean = dirFor(id).exists()

    fun hasOriginal(id: String): Boolean = File(dirFor(id), ORIGINAL_FILE).isFile

    /** Frees the space of original.gif (the animation can no longer be re-edited). */
    fun deleteOriginal(id: String): Boolean = File(dirFor(id), ORIGINAL_FILE).delete()

    /** Everything this animation takes on disk (original, frames, thumbnails, meta). */
    fun diskBytes(id: String): Long = dirFor(id).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** A scratch folder next to the library (name ends in ".part": list() skips it). Emptied on creation. */
    fun newStagingDir(id: String): File {
        require(AnimationMeta.isValidId(id)) { "Bad animation id" }
        val dir = File(root, "$id.part")
        dir.deleteRecursively()
        if (!dir.mkdirs()) throw IOException("Cannot create $dir")
        return dir
    }

    /** Moves a finished staging folder into place (atomic rename inside the same folder). */
    fun commitStaging(staging: File, id: String): Boolean = staging.renameTo(dirFor(id))

    /** Reads one animation's meta.json (null if missing or broken). */
    fun read(id: String): AnimationMeta? = readMeta(dirFor(id))

    /** Deletes the whole animation folder (original, frames, thumbnails). */
    fun delete(id: String): Boolean = dirFor(id).deleteRecursively()

    /** Copies an animation under a new id and a name like "Name (copy)". Returns null on failure. */
    fun duplicate(id: String, copySuffix: String): AnimationMeta? {
        val srcDir = dirFor(id)
        val old = readMeta(srcDir) ?: return null
        val newId = UUID.randomUUID().toString()
        val newDir = dirFor(newId)
        return try {
            if (!srcDir.copyRecursively(newDir)) throw IOException("copy failed")
            File(newDir, ThumbnailStore.DIR_NAME).deleteRecursively() // regenerated on demand
            val copy = old.copy(
                id = newId,
                name = AnimationNames.sanitize("${old.name} $copySuffix", old.name),
                createdAt = System.currentTimeMillis(),
                favorite = false,
            )
            writeMeta(copy)
            copy
        } catch (e: IOException) {
            newDir.deleteRecursively()
            null
        }
    }

    private fun readMeta(dir: File): AnimationMeta? {
        val file = File(dir, META_FILE)
        if (!file.isFile || file.length() > MAX_META_BYTES) return null
        val meta = try {
            AnimationMeta.fromJson(file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            null
        }
        return meta?.takeIf { it.id == dir.name }
    }

    companion object {
        const val META_FILE = "meta.json"
        const val ORIGINAL_FILE = "original.gif"
        const val FRAMES_DIR = "frames"

        /** Optional second set of frames for the "Máxima calidad" mode (full speed, big, no memory fit). */
        const val HQ_FRAMES_DIR = "frames_hq"
        private const val MAX_META_BYTES = 64 * 1024L
    }
}
