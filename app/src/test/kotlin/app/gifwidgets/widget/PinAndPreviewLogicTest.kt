package app.gifwidgets.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAndPreviewLogicTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun pendingRoundTripsWhileFresh() {
        val text = PendingAssignmentLogic.encode(id, 1_000L)
        assertEquals(id, PendingAssignmentLogic.decode(text, 1_000L))
        assertEquals(id, PendingAssignmentLogic.decode(text, 1_000L + PendingAssignmentLogic.TTL_MS))
    }

    @Test
    fun pendingExpires() {
        val text = PendingAssignmentLogic.encode(id, 1_000L)
        assertNull(PendingAssignmentLogic.decode(text, 1_000L + PendingAssignmentLogic.TTL_MS + 1))
    }

    @Test
    fun pendingFromTheFutureIsRejected() {
        assertNull(PendingAssignmentLogic.decode(PendingAssignmentLogic.encode(id, 5_000L), 4_000L))
    }

    @Test
    fun brokenPendingTextIsRejected() {
        assertNull(PendingAssignmentLogic.decode("", 1L))
        assertNull(PendingAssignmentLogic.decode("|5", 5L))
        assertNull(PendingAssignmentLogic.decode("abc", 5L))
        assertNull(PendingAssignmentLogic.decode("$id|notanumber", 5L))
        assertNull(PendingAssignmentLogic.decode("$id|1|2", 5L))
    }

    @Test
    fun signatureNamesTheAnimationAndItsEdit() {
        assertEquals(PreviewPolicy.NONE, PreviewPolicy.signature(null, 7L))
        assertEquals("$id:7", PreviewPolicy.signature(id, 7L))
    }

    @Test
    fun previewIsSetTheFirstTimeAndAfterEveryChange() {
        val sig = PreviewPolicy.signature(id, 1L)
        assertTrue(PreviewPolicy.shouldAttempt(null, 0L, sig, 100L, 600L))
        assertFalse(PreviewPolicy.shouldAttempt(sig, 50L, sig, 10_000L, 600L)) // unchanged: never call again
        assertTrue(PreviewPolicy.shouldAttempt(sig, 50L, PreviewPolicy.signature(id, 2L), 10_000L, 600L)) // edited
    }

    @Test
    fun aRateLimitedAttemptWaitsBeforeTryingAgain() {
        val sig = PreviewPolicy.signature(id, 1L)
        assertFalse(PreviewPolicy.shouldAttempt(null, 1_000L, sig, 1_100L, 600L))
        assertTrue(PreviewPolicy.shouldAttempt(null, 1_000L, sig, 1_600L, 600L))
        assertTrue(PreviewPolicy.shouldAttempt(null, 1_000L, sig, 500L, 600L)) // clock went backwards
    }

    @Test
    fun emptyLibraryOnlyRemovesAPreviewThatWasSet() {
        assertFalse(PreviewPolicy.shouldAttempt(null, 0L, PreviewPolicy.NONE, 100L, 600L))
        assertFalse(PreviewPolicy.shouldAttempt(PreviewPolicy.NONE, 0L, PreviewPolicy.NONE, 100L, 600L))
        assertTrue(PreviewPolicy.shouldAttempt("$id:1", 0L, PreviewPolicy.NONE, 100L, 600L))
    }
}
