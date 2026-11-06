package app.gifwidgets.library

import android.content.Context
import android.graphics.BitmapFactory
import app.gifwidgets.edit.AlphaScan

/**
 * Old library entries say "has transparency" whenever the GIF carried a "transparent colour" flag, which many
 * opaque GIFs do. This looks at the saved frames once and stores the real answer (alphaChecked = true).
 * Call from a background thread. It stops at the first see-through pixel, so a GIF that really has
 * transparency is settled by its first frame; an opaque one is read once, frame by frame, and never again.
 */
object AlphaCheck {

    /** Returns true when at least one entry changed. */
    fun verifyAll(context: Context): Boolean {
        val store = LibraryStore(context)
        var changed = false
        for (meta in store.list()) {
            if (meta.alphaChecked) continue
            val real = if (meta.isOptimized && meta.hasAlpha) (hasRealAlpha(store, meta) ?: continue) else meta.hasAlpha
            if (real != meta.hasAlpha) changed = true
            store.writeMeta(meta.copy(hasAlpha = real, alphaChecked = true))
        }
        return changed
    }

    /** Null when the frames cannot be read (the entry is left as it is and tried again next time). */
    private fun hasRealAlpha(store: LibraryStore, meta: AnimationMeta): Boolean? {
        val files = store.framesDir(meta.id).listFiles { f -> f.name.endsWith(".webp") }?.sortedBy { it.name }
        if (files.isNullOrEmpty()) return null
        for (file in files) {
            val bmp = BitmapFactory.decodeFile(file.path) ?: return null
            try {
                if (!bmp.hasAlpha()) continue
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                if (AlphaScan.anyTransparent(pixels)) return true
            } finally {
                bmp.recycle()
            }
        }
        return false
    }
}
