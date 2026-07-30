package com.echomind.data.export

import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity
import org.junit.Assert.assertEquals
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
                AiHypothesisEntity(8, 7, """{"thesis":"Draft"}""", "Alternative", "proposed", 21)
            ),
            conclusions = listOf(
                ConclusionEntity(9, 7, 10, 22)
            ),
            revisions = listOf(
                ConclusionRevisionEntity(10, 9, 1, "Confirmed", "user", 23)
            ),
            evidenceLinks = listOf(
                EvidenceLinkEntity(11, 10, 7, "supports", "confirmed")
            )
        )

        val manifest = buildManifest(snapshot, exportedAt = 99)

        assertEquals(2, manifest.version)
        assertEquals(99L, manifest.exportedAt)
        assertEquals("legacy_unconfirmed", manifest.entries.single().analysisStatus)
        assertEquals("entry_7.m4a.wav", manifest.rawRecords.single().audioFileName)
        assertEquals(7L, manifest.hypotheses.single().rawRecordId)
        assertEquals(10L, manifest.conclusions.single().currentRevisionId)
        assertEquals(9L, manifest.revisions.single().conclusionId)
        assertEquals(7L, manifest.evidenceLinks.single().sourceRawRecordId)
    }
}
