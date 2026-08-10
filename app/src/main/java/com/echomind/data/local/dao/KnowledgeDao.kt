package com.echomind.data.local.dao

import androidx.room.Dao
import androidx.room.ColumnInfo
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
import com.echomind.data.local.entity.AudioCleanupEntity

data class SearchConclusionRow(
    @ColumnInfo(name = "revision_id") val revisionId: Long,
    @ColumnInfo(name = "conclusion_id") val conclusionId: Long,
    @ColumnInfo(name = "legacy_entry_id") val legacyEntryId: Long?,
    val text: String,
    val version: Int,
    @ColumnInfo(name = "revision_created_at") val revisionCreatedAt: Long,
    @ColumnInfo(name = "current_revision_id") val currentRevisionId: Long?
)

data class SearchRawRow(
    @ColumnInfo(name = "raw_record_id") val rawRecordId: Long,
    @ColumnInfo(name = "legacy_entry_id") val legacyEntryId: Long?,
    @ColumnInfo(name = "original_text") val originalText: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

data class SearchThemeRow(
    @ColumnInfo(name = "theme_id") val themeId: Long,
    val name: String,
    @ColumnInfo(name = "conclusion_count") val conclusionCount: Int
)

data class LinkCandidateRawRow(
    @ColumnInfo(name = "raw_record_id") val rawRecordId: Long,
    @ColumnInfo(name = "original_text") val originalText: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long
)

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
        "SELECT DISTINCT raw_records.* FROM raw_records " +
            "INNER JOIN conclusions ON conclusions.raw_record_id = raw_records.id " +
            "WHERE conclusions.current_revision_id IS NOT NULL " +
            "ORDER BY raw_records.created_at, raw_records.id"
    )
    suspend fun getRawRecordsForCurrentConclusions(): List<RawRecordEntity>

    @Query(
        "SELECT * FROM raw_records WHERE id NOT IN (:excludedIds) " +
            "ORDER BY created_at DESC, id DESC"
    )
    suspend fun getRawRecordsExcluding(excludedIds: List<Long>): List<RawRecordEntity>
    @Query("SELECT * FROM ai_hypotheses ORDER BY created_at, id")
    suspend fun getAllHypotheses(): List<AiHypothesisEntity>

    @Query(
        "SELECT * FROM ai_hypotheses WHERE status = 'proposed' COLLATE NOCASE " +
            "ORDER BY created_at, id"
    )
    suspend fun getProposedHypotheses(): List<AiHypothesisEntity>

    @Query("SELECT * FROM conclusions ORDER BY created_at, id")
    suspend fun getAllConclusions(): List<ConclusionEntity>

    @Query("SELECT * FROM conclusion_revisions ORDER BY conclusion_id, version")
    suspend fun getAllRevisions(): List<ConclusionRevisionEntity>

    @Query(
        "SELECT conclusion_revisions.* FROM conclusion_revisions " +
            "INNER JOIN conclusions " +
            "ON conclusions.current_revision_id = conclusion_revisions.id " +
            "ORDER BY conclusion_revisions.conclusion_id, conclusion_revisions.version"
    )
    suspend fun getCurrentRevisions(): List<ConclusionRevisionEntity>

    @Query("SELECT * FROM evidence_links ORDER BY id")
    suspend fun getAllEvidenceLinks(): List<EvidenceLinkEntity>

    @Query(
        "SELECT DISTINCT evidence_links.* FROM evidence_links " +
            "INNER JOIN conclusions " +
            "ON conclusions.current_revision_id = evidence_links.conclusion_revision_id " +
            "ORDER BY evidence_links.id"
    )
    suspend fun getEvidenceLinksForCurrentRevisions(): List<EvidenceLinkEntity>

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

    @Query("SELECT * FROM ai_hypotheses WHERE raw_record_id = :rawRecordId ORDER BY id")
    suspend fun getHypothesesForRawRecord(rawRecordId: Long): List<AiHypothesisEntity>

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

    @Query(
        "SELECT DISTINCT conclusion_revisions.* FROM conclusion_revisions " +
            "INNER JOIN decisions ON decisions.source_revision_id = conclusion_revisions.id"
    )
    suspend fun getRevisionsForDecisions(): List<ConclusionRevisionEntity>

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

    @Query(
        "SELECT DISTINCT theme_links.* FROM theme_links " +
            "INNER JOIN conclusions " +
            "ON conclusions.current_revision_id = theme_links.conclusion_revision_id " +
            "WHERE theme_links.confirmed = 1"
    )
    suspend fun getConfirmedThemeLinksForCurrentRevisions(): List<ThemeLinkEntity>

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
        "SELECT id AS raw_record_id, legacy_entry_id, original_text, created_at " +
            "FROM raw_records WHERE original_text LIKE '%' || :query || '%' " +
            "ESCAPE '\\' ORDER BY created_at DESC, id DESC"
    )
    suspend fun searchRawRows(query: String): List<SearchRawRow>

    @Query(
        "SELECT conclusion_revisions.id AS revision_id, " +
            "conclusion_revisions.conclusion_id AS conclusion_id, " +
            "raw_records.legacy_entry_id AS legacy_entry_id, " +
            "conclusion_revisions.text AS text, " +
            "conclusion_revisions.version AS version, " +
            "conclusion_revisions.created_at AS revision_created_at, " +
            "conclusions.current_revision_id AS current_revision_id " +
            "FROM conclusion_revisions " +
            "INNER JOIN conclusions ON conclusions.id = conclusion_revisions.conclusion_id " +
            "INNER JOIN raw_records ON raw_records.id = conclusions.raw_record_id " +
            "WHERE conclusion_revisions.text LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY conclusion_revisions.created_at DESC, conclusion_revisions.id DESC"
    )
    suspend fun searchConclusionRows(query: String): List<SearchConclusionRow>

    @Query(
        "SELECT themes.id AS theme_id, themes.name AS name, COUNT(theme_links.id) AS conclusion_count " +
            "FROM themes LEFT JOIN theme_links " +
            "ON theme_links.theme_id = themes.id AND theme_links.confirmed = 1 " +
            "WHERE themes.name LIKE '%' || :query || '%' ESCAPE '\\' " +
            "GROUP BY themes.id ORDER BY themes.name"
    )
    suspend fun searchThemeRows(query: String): List<SearchThemeRow>

    @Query(
        "SELECT themes.name FROM themes " +
            "INNER JOIN theme_links ON theme_links.theme_id = themes.id " +
            "WHERE theme_links.conclusion_revision_id = :revisionId " +
            "AND theme_links.confirmed = 1 ORDER BY themes.id"
    )
    suspend fun getConfirmedThemeNamesForRevision(revisionId: Long): List<String>

    @Query(
        "SELECT id AS raw_record_id, original_text AS original_text, " +
            "created_at AS recorded_at FROM raw_records " +
            "WHERE :currentRawRecordId IS NULL OR id != :currentRawRecordId " +
            "ORDER BY created_at DESC, id DESC"
    )
    suspend fun getRawRecordsForLinkCandidates(currentRawRecordId: Long?): List<LinkCandidateRawRow>

    @Query(
        "SELECT raw_records.id AS raw_record_id, " +
            "raw_records.original_text AS original_text, " +
            "raw_records.created_at AS recorded_at " +
            "FROM raw_records " +
            "WHERE (:currentRawRecordId IS NULL OR raw_records.id != :currentRawRecordId) " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM evidence_links " +
            "WHERE evidence_links.conclusion_revision_id = :revisionId " +
            "AND evidence_links.source_raw_record_id = raw_records.id" +
            ") " +
            "AND (:query = '' OR raw_records.original_text_search_key LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY raw_records.created_at DESC, raw_records.id DESC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun getManualLinkCandidateRows(
        revisionId: Long,
        currentRawRecordId: Long?,
        query: String,
        limit: Int,
        offset: Int
    ): List<LinkCandidateRawRow>

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

    @Query(
        "SELECT DISTINCT decisions.* FROM decisions " +
            "INNER JOIN conclusions " +
            "ON conclusions.current_revision_id = decisions.source_revision_id " +
            "ORDER BY decisions.created_at DESC, decisions.id DESC"
    )
    suspend fun getDecisionsForCurrentRevisions(): List<DecisionEntity>

    @Query("SELECT * FROM outcomes ORDER BY created_at DESC, id DESC")
    suspend fun getAllOutcomes(): List<OutcomeEntity>

    @Query(
        "SELECT DISTINCT outcomes.* FROM outcomes " +
            "INNER JOIN decisions ON decisions.id = outcomes.decision_id " +
            "INNER JOIN conclusions " +
            "ON conclusions.current_revision_id = decisions.source_revision_id " +
            "ORDER BY outcomes.created_at DESC, outcomes.id DESC"
    )
    suspend fun getOutcomesForCurrentRevisionDecisions(): List<OutcomeEntity>

    @Query("SELECT * FROM decisions WHERE id = :id")
    suspend fun getDecisionById(id: Long): DecisionEntity?

    @Query("SELECT * FROM decisions WHERE source_revision_id = :revisionId")
    suspend fun getDecisionsForSourceRevision(revisionId: Long): List<DecisionEntity>

    @Query("SELECT * FROM outcomes WHERE decision_id = :decisionId ORDER BY created_at, id")
    suspend fun getOutcomesForDecision(decisionId: Long): List<OutcomeEntity>

    @Query(
        "SELECT outcomes.* FROM outcomes " +
            "INNER JOIN decisions ON decisions.id = outcomes.decision_id " +
            "ORDER BY outcomes.decision_id, outcomes.created_at, outcomes.id"
    )
    suspend fun getOutcomesForAllDecisions(): List<OutcomeEntity>

    @Query(
        "UPDATE decisions SET choice = :choice WHERE id = :id AND choice IS NULL"
    )
    suspend fun setDecisionChoice(id: Long, choice: String): Int

    @Query(
        "UPDATE decisions SET choice = :choice WHERE id = :id AND choice IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM outcomes WHERE decision_id = :id)"
    )
    suspend fun replaceDecisionChoice(id: Long, choice: String): Int

    @Query(
        "UPDATE decisions SET source_revision_id = :revisionId " +
            "WHERE id = :id AND choice IS NULL"
    )
    suspend fun replaceDecisionGrounds(id: Long, revisionId: Long): Int

    @Query("DELETE FROM outcomes WHERE decision_id = :decisionId")
    suspend fun deleteOutcomesForDecision(decisionId: Long): Int

    @Query("DELETE FROM outcomes WHERE id = :outcomeId AND decision_id = :decisionId")
    suspend fun deleteOutcome(decisionId: Long, outcomeId: Long): Int

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudioCleanup(cleanup: AudioCleanupEntity)

    @Query("SELECT * FROM audio_cleanup_queue WHERE path = :path")
    suspend fun getAudioCleanup(path: String): AudioCleanupEntity?

    @Query("SELECT COUNT(*) FROM audio_cleanup_queue")
    suspend fun getAudioCleanupCount(): Int

    @Query(
        "SELECT * FROM audio_cleanup_queue " +
            "WHERE attempt_count < :maxAttempts " +
            "ORDER BY failed_at, path LIMIT :limit"
    )
    suspend fun getPendingAudioCleanup(limit: Int, maxAttempts: Int): List<AudioCleanupEntity>

    @Query("DELETE FROM audio_cleanup_queue WHERE path = :path")
    suspend fun deleteAudioCleanup(path: String): Int

    @Query("SELECT * FROM evidence_links WHERE source_raw_record_id = :sourceId ORDER BY id")
    suspend fun getIncomingEvidenceLinks(sourceId: Long): List<EvidenceLinkEntity>

    @Query("DELETE FROM evidence_links WHERE id = :linkId")
    suspend fun deleteEvidenceLinkById(linkId: Long): Int
}
