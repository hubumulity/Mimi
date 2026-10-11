package app.yuxino.mimi.android.provider

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import app.yuxino.mimi.android.R
import app.yuxino.mimi.android.runtimeFeedbackResource
import app.yuxino.mimi.android.CaptionDisplayMode
import app.yuxino.mimi.android.forReading

/** Feed real Rust reducer snapshots through the production session-to-UI boundary. */
class RuntimeSubtitleProjectionTest {
    @Before fun reset() { SubtitleBus.clear(); SubtitleBus.setHistoryLimit(2) }
    @After fun cleanup() { SubtitleBus.clear(); SubtitleBus.setHistoryLimit(0) }

    private class Snapshots(private val atomic: Boolean, historyLimit: Int = 2) {
        private var response = SharedSubtitleCore.exchange(JSONObject().put("operation",
            JSONObject().put("type", "create").put("history_limit", historyLimit)))
        fun apply(event: JSONObject) {
            response = SharedSubtitleCore.exchange(JSONObject().put("state", response.getString("state"))
                .put("operation", JSONObject().put("type", "apply").put("event", event)))
            SubtitleBus.onRuntimeSnapshot(JSONObject().put("atomicPreview", atomic).put("detectedLanguage", "en")
                .put("snapshot", response.getJSONObject("snapshot")), false)
        }
        fun feedback(fields: JSONObject) = SubtitleBus.onRuntimeSnapshot(fields.put("atomicPreview", atomic)
            .put("snapshot", response.getJSONObject("snapshot")), false)
    }
    private fun pair(id: String, source: String, translation: String) = JSONObject()
        .put("type", "identified_final_pair").put("utterance_id", id).put("source", source).put("translation", translation)
    private fun draft(role: String, text: String) = JSONObject().put("type", "utterance_text")
        .put("utterance_id", "B").put("role", role).put("text", text).put("is_final", false)

    @Test fun pendingRealtimeTranslationKeepsAWholeConfirmedReadingPairWithoutHistory() {
        SubtitleBus.setHistoryLimit(0)
        val snapshots = Snapshots(false, 0)
        snapshots.apply(pair("A", "Synthetic A.", "合成 A。"))
        val confirmed = SubtitleBus.displaySnapshot().createdAt
        snapshots.apply(draft("source", "Synthetic B."))
        val pending = SubtitleBus.displaySnapshot()
        assertEquals("Synthetic B.", pending.source)
        assertEquals("", pending.translation)
        assertTrue(pending.history.isEmpty())
        for (mode in listOf(CaptionDisplayMode.BILINGUAL, CaptionDisplayMode.TRANSLATION)) {
            val reading = pending.forReading(mode)
            assertEquals("Synthetic A.", reading.source)
            assertEquals("合成 A。", reading.translation)
            assertTrue(reading.confirmed)
            assertEquals(confirmed, reading.createdAt)
        }
        assertEquals(pending, pending.forReading(CaptionDisplayMode.ORIGINAL))
        snapshots.apply(draft("translation", "合成 B。"))
        for (mode in CaptionDisplayMode.entries) {
            val reading = SubtitleBus.displaySnapshot().forReading(mode)
            assertEquals("Synthetic B.", reading.source)
            assertEquals("合成 B。", reading.translation)
            assertFalse(reading.confirmed)
            assertNull(reading.createdAt)
        }
        SubtitleBus.clear()
        for (mode in CaptionDisplayMode.entries) assertEquals("", SubtitleBus.displaySnapshot().forReading(mode).translation)
    }

    @Test fun firstDraftAndHiddenFramesCannotInventOrReviveAConfirmedTranslation() {
        val snapshots = Snapshots(false)
        snapshots.apply(draft("source", "Synthetic first draft."))
        for (mode in CaptionDisplayMode.entries) {
            val state = SubtitleBus.displaySnapshot()
            assertEquals(state, state.forReading(mode))
        }
        snapshots.apply(pair("A", "Synthetic A.", "合成 A。"))
        snapshots.apply(draft("source", "Synthetic B."))
        val hidden = SubtitleBus.displaySnapshot().copy(hidden = true)
        for (mode in CaptionDisplayMode.entries) assertEquals(hidden, hidden.forReading(mode))
    }

    @Test fun originalReadingFollowsRecognitionWhileAtomicTranslationRetainsItsPreviousPair() {
        SubtitleBus.setHistoryLimit(0)
        val snapshots = Snapshots(true, 0)
        snapshots.apply(pair("A", "Synthetic A.", "合成 A。"))
        snapshots.apply(draft("source", "Synthetic B."))
        val pending = SubtitleBus.displaySnapshot()
        val original = pending.forReading(CaptionDisplayMode.ORIGINAL)
        assertEquals("Synthetic B.", original.source)
        assertFalse(original.confirmed)
        assertNull(original.createdAt)
        for (mode in listOf(CaptionDisplayMode.BILINGUAL, CaptionDisplayMode.TRANSLATION)) {
            assertEquals("Synthetic A.", pending.forReading(mode).source)
            assertEquals("合成 A。", pending.forReading(mode).translation)
        }
        snapshots.apply(draft("source", "Synthetic B.").put("is_final", true))
        val recognized = SubtitleBus.displaySnapshot().forReading(CaptionDisplayMode.ORIGINAL)
        assertEquals("Synthetic B.", recognized.source)
        assertFalse(recognized.confirmed)
        assertNull(recognized.createdAt)
        snapshots.apply(pair("B", "Synthetic B.", "合成 B。"))
        val complete = SubtitleBus.displaySnapshot().forReading(CaptionDisplayMode.ORIGINAL)
        assertEquals("Synthetic B.", complete.source)
        assertTrue(complete.confirmed)
        assertNotNull(complete.createdAt)
        snapshots.apply(draft("source", "Synthetic B.").put("utterance_id", "C").put("is_final", true))
        val repeated = SubtitleBus.displaySnapshot().forReading(CaptionDisplayMode.ORIGINAL)
        assertEquals("Synthetic B.", repeated.source)
        assertFalse(repeated.confirmed)
        assertNull(repeated.createdAt)
    }

    @Test fun originalReadingKeepsCompletedWordsAcrossEmptyAsrHandoffsWithoutHistory() {
        SubtitleBus.setHistoryLimit(0)
        for (atomic in listOf(false, true)) {
            SubtitleBus.clear()
            val snapshots = Snapshots(atomic, 0)
            snapshots.apply(pair("A", "Synthetic completed original.", "合成已完成译文。"))
            val confirmedTime = SubtitleBus.displaySnapshot().createdAt
            snapshots.apply(draft("source", " \n"))
            val gap = SubtitleBus.displaySnapshot()
            assertTrue(gap.currentSource.text.isBlank())
            assertTrue(gap.history.isEmpty())
            val reading = gap.forReading(CaptionDisplayMode.ORIGINAL)
            assertEquals("Synthetic completed original.", reading.source)
            assertTrue(reading.confirmed)
            assertEquals(confirmedTime, reading.createdAt)
            val hidden = gap.copy(hidden = true)
            assertEquals(hidden, hidden.forReading(CaptionDisplayMode.ORIGINAL))
            snapshots.apply(draft("source", "Synthetic next original"))
            val next = SubtitleBus.displaySnapshot().forReading(CaptionDisplayMode.ORIGINAL)
            assertEquals("Synthetic next original", next.source)
            assertFalse(next.confirmed)
            assertNull(next.createdAt)
            SubtitleBus.clear()
            assertEquals("", SubtitleBus.displaySnapshot().forReading(CaptionDisplayMode.ORIGINAL).source)
        }
    }

    @Test fun emptyOriginalHandoffCanKeepTheSharedCompletePreviewWithoutConfirmingIt() {
        SubtitleBus.setHistoryLimit(0)
        val snapshots = Snapshots(true, 0)
        snapshots.apply(draft("source", "Synthetic complete preview source."))
        snapshots.apply(JSONObject().put("type", "preview_pair").put("source_utterance_id", JSONObject.NULL)
            .put("source", "Synthetic complete preview source.").put("translation", "合成完整预览译文。"))
        snapshots.apply(draft("source", ""))
        val gap = SubtitleBus.displaySnapshot()
        assertNull(gap.retainedConfirmed)
        assertTrue(gap.currentSource.text.isBlank())
        val reading = gap.forReading(CaptionDisplayMode.ORIGINAL)
        assertEquals("Synthetic complete preview source.", reading.source)
        assertFalse(reading.confirmed)
        assertNull(reading.createdAt)
        snapshots.apply(draft("source", "Synthetic new original"))
        assertEquals("Synthetic new original", SubtitleBus.displaySnapshot().forReading(CaptionDisplayMode.ORIGINAL).source)
    }

    @Test fun recoveryFeedbackPreservesCaptionsAndDistinguishesScheduledRetriesFromNewSpeech() {
        val snapshots = Snapshots(true)
        snapshots.apply(pair("A", "Synthetic A.", "合成 A。"))
        for ((reason, retryResource, waitingResource) in listOf(
            Triple("rateLimited", R.string.runtime_rate_retry, R.string.runtime_rate_wait),
            Triple("temporarilyUnavailable", R.string.runtime_unavailable_retry, R.string.runtime_unavailable_wait),
        )) {
            for (scheduled in listOf(true, false)) {
                snapshots.feedback(JSONObject().put("connectionStatus", "listening")
                    .put("translationRecovery", JSONObject().put("reason", reason).put("retryScheduled", scheduled)))
                assertEquals(if (scheduled) retryResource else waitingResource, runtimeFeedbackResource(SubtitleBus.runtimeFeedback))
                assertEquals("Synthetic A.", SubtitleBus.displaySource)
                assertEquals("合成 A。", SubtitleBus.displayTranslation)
            }
        }
        snapshots.feedback(JSONObject().put("connectionStatus", "connecting"))
        assertEquals(R.string.runtime_connecting, runtimeFeedbackResource(SubtitleBus.runtimeFeedback))
        snapshots.feedback(JSONObject().put("connectionStatus", "listening").put("isTranslationTimedOut", true))
        assertEquals(R.string.runtime_translation_delayed, runtimeFeedbackResource(SubtitleBus.runtimeFeedback))
        snapshots.feedback(JSONObject().put("connectionStatus", "listening"))
        assertNull(runtimeFeedbackResource(SubtitleBus.runtimeFeedback))
        snapshots.feedback(JSONObject().put("translationRecovery", JSONObject().put("reason", "synthetic-private-error")))
        assertNull(runtimeFeedbackResource(SubtitleBus.runtimeFeedback))
        SubtitleBus.clear()
        assertNull(runtimeFeedbackResource(SubtitleBus.runtimeFeedback))
    }

    @Test fun atomicRuntimeRetainsTheCompletePairUntilTheNextCompletePreviewArrives() {
        val snapshots = Snapshots(true)
        snapshots.apply(pair("A", "Synthetic A.", "合成 A。"))
        snapshots.apply(draft("source", "Synthetic B."))
        snapshots.apply(draft("translation", "合成 B。"))
        assertEquals("Synthetic A.", SubtitleBus.displaySource)
        assertEquals("合成 A。", SubtitleBus.displayTranslation)
        assertTrue(SubtitleBus.displayPairFinal)
        assertEquals("Synthetic B.", SubtitleBus.sourceDraft)
        snapshots.apply(JSONObject().put("type", "preview_pair").put("source_utterance_id", JSONObject.NULL)
            .put("source", "Synthetic B.").put("translation", "合成 B。"))
        assertEquals("Synthetic B.", SubtitleBus.displaySource)
        assertEquals("合成 B。", SubtitleBus.displayTranslation)
        assertFalse(SubtitleBus.displayPairFinal)
        assertEquals(1, SubtitleBus.historySnapshot().size)
        SubtitleBus.setHistoryLimit(1)
        assertEquals("合成 B。", SubtitleBus.displayTranslation)
        snapshots.apply(pair("B", "Synthetic B.", "合成 B。"))
        assertTrue(SubtitleBus.displayPairFinal)
        assertEquals("合成 B。", SubtitleBus.historySnapshot().single().translation)
    }

    @Test fun independentLaneRuntimeAdvancesDraftsAndCannotCombineDifferentSentenceOwners() {
        val snapshots = Snapshots(false)
        snapshots.apply(pair("A", "Synthetic A.", "合成 A。"))
        snapshots.apply(draft("source", "Synthetic B."))
        assertEquals("Synthetic B.", SubtitleBus.displaySource)
        assertEquals("", SubtitleBus.displayTranslation)
        assertFalse(SubtitleBus.displayPairFinal)
        snapshots.apply(draft("translation", "合成 B。"))
        assertEquals("Synthetic B.", SubtitleBus.displaySource)
        assertEquals("合成 B。", SubtitleBus.displayTranslation)
        assertEquals(1, SubtitleBus.historySnapshot().size)
        SubtitleBus.setHistoryLimit(1)
        assertEquals("Synthetic B.", SubtitleBus.displaySource)
        assertFalse(SubtitleBus.displayPairFinal)
        snapshots.apply(pair("B", "Synthetic B.", "合成 B。"))
        assertTrue(SubtitleBus.displayPairFinal)
        assertEquals("合成 B。", SubtitleBus.historySnapshot().single().translation)
    }
}
