package com.echomind.data.export

import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExportManifestTest {

    @Test
    fun `manifest preserves graph ids and labels legacy analysis unconfirmed`() {
        val snapshot = ExportSnapshot(
            entries = listOf(
                EntryEntity(
                    id = 7,
                    transcript = "Original",
                    audioPath = "entry_7.m4a.enc",
                    durationMs = 10,
                    createdAt = 20,
                    summary = "Generated summary"
                )
            ),
            rawRecords = listOf(
                RawRecordEntity(7, 7, "Original", "entry_7.m4a.enc", 10, 20)
            ),
            hypotheses = listOf(
                AiHypothesisEntity(
                    id = 8,
                    rawRecordId = 7,
                    draftJson = """{"thesis":"Draft"}""",
                    counterargument = "Alternative",
                    status = "proposed",
                    createdAt = 21
                ),
                AiHypothesisEntity(
                    id = 16,
                    rawRecordId = 7,
                    draftJson = """{"thesis":"Follow-up draft"}""",
                    counterargument = "Follow-up alternative",
                    status = "proposed",
                    parentHypothesisId = 8,
                    followUpQuestion = "What would change this?",
                    createdAt = 28
                )
            ),
            conclusions = listOf(
                ConclusionEntity(9, 7, 10, 22)
            ),
            revisions = listOf(
                ConclusionRevisionEntity(10, 9, 1, "Confirmed", "user", 23)
            ),
            evidenceLinks = listOf(
                EvidenceLinkEntity(11, 10, 7, "supports", "confirmed")
            ),
            themes = listOf(
                ThemeEntity(12, "Career", 24, null)
            ),
            themeLinks = listOf(
                ThemeLinkEntity(13, 12, 10, true, 25)
            ),
            decisions = listOf(
                DecisionEntity(
                    id = 14,
                    question = "Should I change roles?",
                    suggestion = "Change roles",
                    choice = null,
                    sourceRevisionId = 10,
                    createdAt = 26,
                    suggestionAuthor = "echomind",
                    suggestionSource = "10",
                    suggestionStatus = "proposal"
                )
            ),
            outcomes = listOf(
                OutcomeEntity(15, 14, "It worked out", 27)
            )
        )

        val manifest = buildManifest(snapshot, exportedAt = 99)

        assertEquals(5, manifest.version)
        assertEquals(99L, manifest.exportedAt)
        assertEquals("legacy_unconfirmed", manifest.entries.single().analysisStatus)
        assertEquals("entry_7.m4a.wav", manifest.rawRecords.single().audioFileName)
        assertEquals(7L, manifest.hypotheses.first().rawRecordId)
        assertEquals(8L, manifest.hypotheses[1].parentHypothesisId)
        assertEquals("What would change this?", manifest.hypotheses[1].followUpQuestion)
        assertEquals(10L, manifest.conclusions.single().currentRevisionId)
        assertEquals(9L, manifest.revisions.single().conclusionId)
        assertEquals(7L, manifest.evidenceLinks.single().sourceRawRecordId)
        assertEquals("Career", manifest.themes.single().name)
        assertEquals(12L, manifest.themeLinks.single().themeId)
        assertEquals(true, manifest.themeLinks.single().confirmed)
        assertEquals("Should I change roles?", manifest.decisions.single().question)
        assertEquals(10L, manifest.decisions.single().sourceRevisionId)
        assertEquals(14L, manifest.outcomes.single().decisionId)
        assertEquals("It worked out", manifest.outcomes.single().report)
    }

    @Test
    fun `hypothesis graph rejects chains and cycles before restore planning`() {
        val root = hypothesis(8)
        val child = hypothesis(16, parentId = root.id)
        val grandchild = hypothesis(24, parentId = child.id)

        assertThrows(IllegalArgumentException::class.java) {
            validateHypothesisGraph(listOf(root, child, grandchild))
        }

        assertThrows(IllegalArgumentException::class.java) {
            validateHypothesisGraph(
                listOf(
                    root.copy(parentHypothesisId = child.id, followUpQuestion = "Cycle A"),
                    child.copy(parentHypothesisId = root.id, followUpQuestion = "Cycle B")
                )
            )
        }

        validateHypothesisGraph(listOf(root, child.copy(status = "confirmed")))
        validateHypothesisGraph(listOf(root, child.copy(status = "rejected")))
    }

    private fun hypothesis(
        id: Long,
        parentId: Long? = null,
        status: String = "proposed"
    ) = ExportHypothesis(
        id = id,
        rawRecordId = 7,
        draftJson = "{}",
        counterargument = "Alternative",
        status = status,
        parentHypothesisId = parentId,
        followUpQuestion = parentId?.let { "Question $id" },
        createdAt = id
    )
}
