package app.gifwidgets.library

/** Favorites and collections (folders) of the library. Pure. */
object LibraryOrganizer {
    const val MAX_COLLECTION_LENGTH = 24

    /** A collection name without control characters or odd spacing, at most [MAX_COLLECTION_LENGTH] long. Blank stays blank. */
    fun cleanCollection(raw: String?): String {
        val s = AnimationNames.sanitize(raw, "")
        return if (s.length > MAX_COLLECTION_LENGTH) s.take(MAX_COLLECTION_LENGTH).trimEnd() else s
    }

    /** The collections in use, without repeats (ignoring case), alphabetically. */
    fun collections(items: List<AnimationMeta>): List<String> =
        items.map { it.collection }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** [collection] empty = no filter. The comparison ignores case. */
    fun inCollection(items: List<AnimationMeta>, collection: String): List<AnimationMeta> =
        if (collection.isEmpty()) items else items.filter { it.collection.equals(collection, ignoreCase = true) }
}
