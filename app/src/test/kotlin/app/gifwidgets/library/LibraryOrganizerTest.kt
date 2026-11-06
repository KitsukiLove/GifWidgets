package app.gifwidgets.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOrganizerTest {
    private fun meta(name: String, createdAt: Long = 0, favorite: Boolean = false, collection: String = "") = AnimationMeta(
        id = "00000000-0000-0000-0000-000000000000", name = name, createdAt = createdAt,
        srcWidth = 1, srcHeight = 1, frameCount = 1, fps = 10, hasAlpha = false, bytes = 1,
        favorite = favorite, collection = collection,
    )

    @Test
    fun collectionNamesAreCleaned() {
        assertEquals("Memes", LibraryOrganizer.cleanCollection("  Memes \n"))
        assertEquals("", LibraryOrganizer.cleanCollection("   "))
        assertEquals("", LibraryOrganizer.cleanCollection(null))
        assertEquals(LibraryOrganizer.MAX_COLLECTION_LENGTH, LibraryOrganizer.cleanCollection("x".repeat(100)).length)
    }

    @Test
    fun collectionsAreUniqueAndAlphabetical() {
        val items = listOf(meta("a", collection = "Zorros"), meta("b", collection = "memes"), meta("c", collection = "Memes"), meta("d"))
        assertEquals(listOf("memes", "Zorros"), LibraryOrganizer.collections(items))
    }

    @Test
    fun filterByCollectionIgnoresCase() {
        val items = listOf(meta("a", collection = "Memes"), meta("b", collection = "Otros"), meta("c"))
        assertEquals(listOf("a"), LibraryOrganizer.inCollection(items, "memes").map { it.name })
        assertEquals(3, LibraryOrganizer.inCollection(items, "").size)
    }

    @Test
    fun favoritesComeFirstAndKeepTheChosenOrder() {
        val items = listOf(
            meta("old", createdAt = 1), meta("fav-old", createdAt = 2, favorite = true),
            meta("new", createdAt = 4), meta("fav-new", createdAt = 5, favorite = true),
        )
        assertEquals(listOf("fav-new", "fav-old", "new", "old"), AnimationSort.sort(items, SortOrder.NEWEST).map { it.name })
        // by name: favorites first (fav-new, fav-old), then new, old
        assertEquals(listOf("fav-new", "fav-old", "new", "old"), AnimationSort.sort(items, SortOrder.NAME).map { it.name })
        assertTrue(AnimationSort.sort(items, SortOrder.SIZE).take(2).all { it.favorite })
    }
}
