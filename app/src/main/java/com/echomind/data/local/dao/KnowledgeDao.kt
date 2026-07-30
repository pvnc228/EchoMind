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

    @Query("DELETE FROM raw_records WHERE legacy_entry_id = :entryId")
    suspend fun deleteRawRecordByLegacyEntryId(entryId: Long): Int

    @Delete
    suspend fun deleteHypothesis(hypothesis: AiHypothesisEntity)

    @Delete
    suspend fun deleteConclusion(conclusion: ConclusionEntity)
}
