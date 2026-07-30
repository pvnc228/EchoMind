package com.echomind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRawRecord(record: RawRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHypothesis(hypothesis: AiHypothesisEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConclusion(conclusion: ConclusionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: ConclusionRevisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidenceLink(link: EvidenceLinkEntity): Long

    @Query("SELECT * FROM raw_records ORDER BY created_at, id")
    suspend fun getAllRawRecords(): List<RawRecordEntity>

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

    @Query("SELECT * FROM conclusion_revisions WHERE id = :id")
    suspend fun getRevisionById(id: Long): ConclusionRevisionEntity?

    @Query("UPDATE conclusions SET current_revision_id = :revisionId WHERE id = :conclusionId")
    suspend fun setCurrentRevision(conclusionId: Long, revisionId: Long): Int

    @Query("DELETE FROM raw_records WHERE legacy_entry_id = :entryId")
    suspend fun deleteRawRecordByLegacyEntryId(entryId: Long): Int

    @Delete
    suspend fun deleteHypothesis(hypothesis: AiHypothesisEntity)

    @Delete
    suspend fun deleteConclusion(conclusion: ConclusionEntity)
}
