package com.echomind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.CaptureDraftEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.local.entity.HomeCardDispositionEntity

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRawRecord(record: RawRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRawRecords(records: List<RawRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHypothesis(hypothesis: AiHypothesisEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHypotheses(hypotheses: List<AiHypothesisEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConclusion(conclusion: ConclusionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConclusions(conclusions: List<ConclusionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: ConclusionRevisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevisions(revisions: List<ConclusionRevisionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvidenceLink(link: EvidenceLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidenceLinks(links: List<EvidenceLinkEntity>)

    @Query("SELECT * FROM raw_records ORDER BY created_at, id")
    suspend fun getAllRawRecords(): List<RawRecordEntity>

    @Query(
        "SELECT * FROM raw_records WHERE id NOT IN (:excludedIds) " +
            "ORDER BY created_at DESC, id DESC"
    )
    suspend fun getRawRecordsExcluding(excludedIds: List<Long>): List<RawRecordEntity>
    @Query("SELECT * FROM ai_hypotheses ORDER BY created_at, id")
    suspend fun getAllHypotheses(): List<AiHypothesisEntity>

    @Query("SELECT * FROM conclusions ORDER BY created_at, id")
    suspend fun getAllConclusions(): List<ConclusionEntity>

    @Query("SELECT * FROM conclusion_revisions ORDER BY conclusion_id, version")
    suspend fun getAllRevisions(): List<ConclusionRevisionEntity>

    @Query("SELECT * FROM evidence_links ORDER BY id")
    suspend fun getAllEvidenceLinks(): List<EvidenceLinkEntity>

    @Query("SELECT * FROM raw_records WHERE id = :id")
    suspend fun getRawRecordById(id: Long): RawRecordEntity?

    @Query("SELECT * FROM raw_records WHERE legacy_entry_id = :entryId")
    suspend fun getRawRecordByLegacyEntryId(entryId: Long): RawRecordEntity?

    @Query("SELECT * FROM ai_hypotheses WHERE id = :id")
    suspend fun getHypothesisById(id: Long): AiHypothesisEntity?

    @Query(
        "SELECT * FROM ai_hypotheses " +
            "WHERE raw_record_id = :rawRecordId ORDER BY created_at DESC, id DESC LIMIT 1"
    )
    suspend fun getLatestHypothesisForRawRecord(rawRecordId: Long): AiHypothesisEntity?

    @Query(
        "SELECT * FROM ai_hypotheses WHERE status = 'proposed' " +
            "ORDER BY created_at DESC, id DESC LIMIT 1"
    )
    suspend fun getLatestProposedHypothesis(): AiHypothesisEntity?

    @Query(
        "UPDATE ai_hypotheses SET status = :newStatus " +
            "WHERE id = :id AND status = 'proposed'"
    )
    suspend fun updateProposedHypothesisStatus(id: Long, newStatus: String): Int

    @Query(
        "SELECT * FROM conclusions WHERE raw_record_id = :rawRecordId " +
            "ORDER BY created_at DESC, id DESC LIMIT 1"
    )
    suspend fun getConclusionForRawRecord(rawRecordId: Long): ConclusionEntity?

    @Query("SELECT * FROM conclusions WHERE id = :id")
    suspend fun getConclusionById(id: Long): ConclusionEntity?

    @Query("SELECT * FROM conclusion_revisions WHERE id = :id")
    suspend fun getRevisionById(id: Long): ConclusionRevisionEntity?

    @Query("SELECT * FROM conclusion_revisions WHERE conclusion_id = :conclusionId ORDER BY version")
    suspend fun getRevisionsForConclusion(conclusionId: Long): List<ConclusionRevisionEntity>

    @Query("SELECT MAX(version) FROM conclusion_revisions WHERE conclusion_id = :conclusionId")
    suspend fun getMaxRevisionVersion(conclusionId: Long): Int?

    @Query(
        "SELECT * FROM evidence_links WHERE conclusion_revision_id = :revisionId " +
            "ORDER BY id LIMIT 1"
    )
    suspend fun getEvidenceLinkForRevision(revisionId: Long): EvidenceLinkEntity?

    @Query("UPDATE conclusions SET current_revision_id = :revisionId WHERE id = :conclusionId")
    suspend fun setCurrentRevision(conclusionId: Long, revisionId: Long): Int

    @Query("DELETE FROM raw_records WHERE legacy_entry_id = :entryId")
    suspend fun deleteRawRecordByLegacyEntryId(entryId: Long): Int

    @Delete
    suspend fun deleteHypothesis(hypothesis: AiHypothesisEntity)

    @Delete
    suspend fun deleteConclusion(conclusion: ConclusionEntity)

    @Query(
        "SELECT * FROM evidence_links " +
            "WHERE conclusion_revision_id = :revisionId AND source_raw_record_id = :sourceId " +
            "LIMIT 1"
    )
    suspend fun getEvidenceLinkForRevisionAndSource(
        revisionId: Long,
        sourceId: Long
    ): EvidenceLinkEntity?

    @Query("SELECT * FROM evidence_links WHERE conclusion_revision_id = :revisionId ORDER BY id")
    suspend fun getEvidenceLinksForRevision(revisionId: Long): List<EvidenceLinkEntity>

    @Query(
        "SELECT * FROM evidence_links WHERE conclusion_revision_id = :revisionId " +
            "AND status != 'confirmed' ORDER BY id"
    )
    suspend fun getPendingEvidenceLinksForRevision(revisionId: Long): List<EvidenceLinkEntity>

    @Query("SELECT * FROM evidence_links WHERE id = :linkId")
    suspend fun getEvidenceLinkById(linkId: Long): EvidenceLinkEntity?

    @Query(
        "UPDATE evidence_links SET status = 'confirmed', origin = 'user_confirmed', " +
            "review_metadata = NULL WHERE id = :linkId AND status != 'confirmed'"
    )
    suspend fun confirmEvidenceLink(linkId: Long): Int

    @Query(
        "DELETE FROM evidence_links " +
            "WHERE conclusion_revision_id = :revisionId AND source_raw_record_id = :sourceId"
    )
    suspend fun deleteEvidenceLink(revisionId: Long, sourceId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTheme(theme: ThemeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertThemes(themes: List<ThemeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertThemeLink(link: ThemeLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertThemeLinks(links: List<ThemeLinkEntity>)

    @Query("SELECT * FROM themes WHERE archived_at IS NULL ORDER BY name")
    suspend fun getActiveThemes(): List<ThemeEntity>

    @Query("SELECT * FROM themes ORDER BY name")
    suspend fun getAllThemes(): List<ThemeEntity>

    @Query("SELECT * FROM theme_links WHERE confirmed = 1")
    suspend fun getConfirmedThemeLinksAll(): List<ThemeLinkEntity>

    @Query("SELECT * FROM theme_links ORDER BY id")
    suspend fun getAllThemeLinks(): List<ThemeLinkEntity>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun getThemeById(id: Long): ThemeEntity?

    @Query("UPDATE themes SET name = :name WHERE id = :id")
    suspend fun renameTheme(id: Long, name: String): Int

    @Query("UPDATE themes SET archived_at = :archivedAt WHERE id = :id")
    suspend fun archiveTheme(id: Long, archivedAt: Long): Int

    @Query(
        "SELECT * FROM theme_links WHERE theme_id = :themeId " +
            "AND conclusion_revision_id = :revisionId AND confirmed = 1 LIMIT 1"
    )
    suspend fun getConfirmedThemeLink(themeId: Long, revisionId: Long): ThemeLinkEntity?

    @Query("SELECT * FROM theme_links WHERE theme_id = :themeId AND confirmed = 1")
    suspend fun getConfirmedLinksForTheme(themeId: Long): List<ThemeLinkEntity>

    @Query("SELECT * FROM theme_links WHERE conclusion_revision_id = :revisionId AND confirmed = 1")
    suspend fun getConfirmedLinksForRevision(revisionId: Long): List<ThemeLinkEntity>

    @Query("SELECT * FROM theme_links WHERE conclusion_revision_id = :revisionId ORDER BY id")
    suspend fun getThemeLinksForRevision(revisionId: Long): List<ThemeLinkEntity>

    @Query(
        "SELECT * FROM theme_links WHERE conclusion_revision_id = :revisionId " +
            "AND confirmed = 0 ORDER BY id"
    )
    suspend fun getPendingThemeLinksForRevision(revisionId: Long): List<ThemeLinkEntity>

    @Query("UPDATE theme_links SET confirmed = 1, origin = 'user_confirmed', review_required = 0 WHERE id = :linkId AND confirmed = 0")
    suspend fun confirmThemeLink(linkId: Long): Int

    @Query("DELETE FROM theme_links WHERE id = :linkId AND confirmed = 0")
    suspend fun rejectThemeLink(linkId: Long): Int

    @Query("DELETE FROM theme_links WHERE theme_id = :themeId AND conclusion_revision_id = :revisionId")
    suspend fun deleteThemeLink(themeId: Long, revisionId: Long): Int

    @Query("DELETE FROM theme_links WHERE theme_id = :themeId")
    suspend fun deleteLinksForTheme(themeId: Long): Int

    @Query("DELETE FROM themes WHERE id = :themeId")
    suspend fun deleteThemeById(themeId: Long): Int

    @Query(
        "SELECT * FROM raw_records WHERE original_text LIKE '%' || :query || '%' " +
            "ESCAPE '\\' ORDER BY created_at DESC, id DESC"
    )
    suspend fun searchRawRecords(query: String): List<RawRecordEntity>

    @Query(
        "SELECT * FROM conclusion_revisions " +
            "WHERE text LIKE '%' || :query || '%' " +
            "ESCAPE '\\' ORDER BY created_at DESC, id DESC"
    )
    suspend fun searchRevisions(query: String): List<ConclusionRevisionEntity>

    @Query(
        "SELECT * FROM themes WHERE name LIKE '%' || :query || '%' " +
            "ESCAPE '\\' ORDER BY name"
    )
    suspend fun searchThemes(query: String): List<ThemeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDecision(decision: DecisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDecisions(decisions: List<DecisionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutcome(outcome: OutcomeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutcomes(outcomes: List<OutcomeEntity>)

    @Query("SELECT * FROM decisions ORDER BY created_at DESC, id DESC")
    suspend fun getAllDecisions(): List<DecisionEntity>

    @Query("SELECT * FROM outcomes ORDER BY created_at DESC, id DESC")
    suspend fun getAllOutcomes(): List<OutcomeEntity>

    @Query("SELECT * FROM decisions WHERE id = :id")
    suspend fun getDecisionById(id: Long): DecisionEntity?

    @Query("SELECT * FROM decisions WHERE source_revision_id = :revisionId")
    suspend fun getDecisionsForSourceRevision(revisionId: Long): List<DecisionEntity>

    @Query("SELECT * FROM outcomes WHERE decision_id = :decisionId ORDER BY created_at, id")
    suspend fun getOutcomesForDecision(decisionId: Long): List<OutcomeEntity>

    @Query(
        "UPDATE decisions SET choice = :choice WHERE id = :id AND choice IS NULL"
    )
    suspend fun setDecisionChoice(id: Long, choice: String): Int

    @Query(
        "UPDATE decisions SET choice = :choice WHERE id = :id AND choice IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM outcomes WHERE decision_id = :id)"
    )
    suspend fun replaceDecisionChoice(id: Long, choice: String): Int

    @Query("DELETE FROM outcomes WHERE decision_id = :decisionId")
    suspend fun deleteOutcomesForDecision(decisionId: Long): Int

    @Query("DELETE FROM decisions WHERE id = :id")
    suspend fun deleteDecisionById(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaptureDraft(draft: CaptureDraftEntity): Long

    @Query("SELECT * FROM capture_drafts WHERE id = 1")
    suspend fun getCaptureDraft(): CaptureDraftEntity?

    @Query("DELETE FROM capture_drafts WHERE id = 1")
    suspend fun deleteCaptureDraft(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHomeCardDisposition(disposition: HomeCardDispositionEntity): Long

    @Query("SELECT * FROM home_card_dispositions ORDER BY created_at, card_key")
    suspend fun getAllHomeCardDispositions(): List<HomeCardDispositionEntity>

    @Query("DELETE FROM home_card_dispositions WHERE card_key = :cardKey")
    suspend fun deleteHomeCardDisposition(cardKey: String): Int

    @Query("SELECT * FROM evidence_links WHERE source_raw_record_id = :sourceId ORDER BY id")
    suspend fun getIncomingEvidenceLinks(sourceId: Long): List<EvidenceLinkEntity>

    @Query("DELETE FROM evidence_links WHERE id = :linkId")
    suspend fun deleteEvidenceLinkById(linkId: Long): Int
}
