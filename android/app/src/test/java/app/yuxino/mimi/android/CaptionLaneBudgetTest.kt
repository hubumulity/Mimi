package app.yuxino.mimi.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionLaneBudgetTest {
    @Test fun aSmallerReferenceFontCannotTakeTheSecondTranslationLine() {
        assertEquals(1 to 2, captionLaneBudget(120, listOf(20, 40, 60, 80, 100, 120), listOf(50, 100, 150)))
        assertEquals(1 to 2, captionLaneBudget(160, listOf(36, 64, 92, 120), listOf(60, 120, 180)))
    }
    @Test fun longOriginalCannotConsumeTheTranslationLane() {
        assertEquals(2 to 2, captionLaneBudget(100, listOf(20, 40, 60, 80, 100, 120), listOf(24, 48, 72, 96)))
    }
    @Test fun contentSizedReadingStillLoansUnusedSpace() {
        assertEquals(1 to 3, captionLaneBudget(100, listOf(20), listOf(24, 48, 72, 96)))
        assertEquals(3 to 1, captionLaneBudget(100, listOf(20, 40, 60, 80, 100), listOf(30)))
    }
    @Test fun fixedSlotsReserveCompleteReadingLinesForBothFonts() {
        assertEquals(40 to 120, captionLaneSlots(160, 36, 60))
        assertEquals(50 to 50, captionLaneSlots(100, 20, 24))
        assertEquals(20 to 50, captionLaneSlots(70, 20, 50))
        assertEquals(0 to 0, captionLaneSlots(0, 20, 50))
    }
    @Test fun fallbackFontTailCannotBorrowTheOtherLanesSpace() {
        val slot = captionLaneSlots(160, 36, 60).second
        assertEquals(0, captionLineBudget(slot, emptyList()))
        assertEquals(2, captionLineBudget(slot, listOf(60, 120, 180)))
        assertEquals(1, captionLineBudget(slot, listOf(70, 140, 210)))
    }
    @Test fun reservationFollowsKnownRouteInsteadOfTextEquality() {
        assertFalse(captionReservesBilingualLanes("en", "en", null))
        assertFalse(captionReservesBilingualLanes("auto", "zh", "zh"))
        assertFalse(captionReservesBilingualLanes("auto", "original", null))
        assertTrue(captionReservesBilingualLanes("auto", "zh", null))
        assertTrue(captionReservesBilingualLanes("en", "zh", null))
        assertTrue(captionReservesBilingualLanes("en", "zh_en", "en"))
    }
    @Test fun singleLanguageUsesAllAvailableReadingSpace() {
        assertEquals(0 to 4, captionLaneBudget(100, emptyList(), listOf(24, 48, 72, 96, 120)))
        assertEquals(4 to 0, captionLaneBudget(80, listOf(20, 40, 60, 80, 100), emptyList()))
        assertEquals(0 to 0, captionLaneBudget(80, emptyList(), emptyList()))
    }
}
