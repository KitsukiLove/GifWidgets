package app.gifwidgets.library

enum class SortOrder { NEWEST, NAME, SIZE }

object AnimationSort {
    /** The chosen order, with the favorites pinned first (the sort is stable, so each group keeps that order). */
    fun sort(items: List<AnimationMeta>, order: SortOrder): List<AnimationMeta> = when (order) {
        SortOrder.NEWEST -> items.sortedByDescending { it.createdAt }
        SortOrder.NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        SortOrder.SIZE -> items.sortedByDescending { it.bytes }
    }.sortedByDescending { it.favorite }
}
