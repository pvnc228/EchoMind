package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.ReflectionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ReflectionRepository @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val knowledgeDao: KnowledgeDao,
    private val analyzer: LocalReflectionAnalyzer,
    private val json: Json
) {
    suspend fun captureRawText(
        originalText: String,
        audioPath: String? = null,
        durationMs: Long = 0
    ): Long {
        require(originalText.isNotBlank()) { "A reflection cannot be blank." }
        val createdAt = System.currentTimeMillis()

        return database.withTransaction {
            val entryId = entryDao.insertEntry(
                EntryEntity(
                    transcript = originalText,
                    audioPath = audioPath,
                    durationMs = durationMs,
                    createdAt = createdAt
                )
            )
            knowledgeDao.insertRawRecord(
                RawRecordEntity(
                    legacyEntryId = entryId,
                    originalText = originalText,
                    audioPath = audioPath,
                    durationMs = durationMs,
                    createdAt = createdAt
                )
            )
        }
    }

    suspend fun createLocalProposal(rawRecordId: Long): ReflectionSession {
        val existing = knowledgeDao.getLatestHypothesisForRawRecord(rawRecordId)
        if (existing != null) {
            return loadReflection(existing.id)
        }

        val rawRecord = requireNotNull(knowledgeDao.getRawRecordById(rawRecordId)) {
            "Raw reflection $rawRecordId does not exist."
        }
        val proposal = analyzer.analyze(rawRecord.originalText)
        val hypothesisId = knowledgeDao.insertHypothesis(
            AiHypothesisEntity(
                rawRecordId = rawRecordId,
                draftJson = json.encodeToString(proposal.draft),
                counterargument = proposal.counterargument,
                status = ReflectionStatus.PROPOSED,
                createdAt = System.currentTimeMillis()
            )
        )
        return loadReflection(hypothesisId)
    }

    suspend fun loadLatestProposedReflection(): ReflectionSession? =
        knowledgeDao.getLatestProposedHypothesis()?.let { loadReflection(it.id) }

    suspend fun loadReflectionForEntry(entryId: Long): ReflectionSession? {
        val rawRecord = knowledgeDao.getRawRecordByLegacyEntryId(entryId) ?: return null
        val hypothesis = knowledgeDao.getLatestHypothesisForRawRecord(rawRecord.id) ?: return null
        return loadReflection(hypothesis.id)
    }

    suspend fun loadReflection(hypothesisId: Long): ReflectionSession {
        val hypothesis = requireNotNull(knowledgeDao.getHypothesisById(hypothesisId)) {
            "Reflection proposal $hypothesisId does not exist."
        }
        val rawRecord = requireNotNull(knowledgeDao.getRawRecordById(hypothesis.rawRecordId)) {
            "Raw reflection ${hypothesis.rawRecordId} does not exist."
        }
        val conclusion = knowledgeDao.getConclusionForRawRecord(rawRecord.id)
        val revision = conclusion?.currentRevisionId?.let {
            knowledgeDao.getRevisionById(it)
        }
        val sourceLink = revision?.let {
            knowledgeDao.getEvidenceLinkForRevision(it.id)
        }

        return ReflectionSession(
            rawRecordId = rawRecord.id,
            hypothesisId = hypothesis.id,
            originalText = rawRecord.originalText,
            draft = json.decodeFromString(hypothesis.draftJson),
            counterargument = hypothesis.counterargument,
            status = hypothesis.status,
            confirmedConclusion = revision?.text,
            revisionVersion = revision?.version,
            revisionId = revision?.id,
            sourceRelationship = sourceLink?.relationship,
            sourceLinkStatus = sourceLink?.status
        )
    }

    suspend fun confirm(hypothesisId: Long, wording: String): ReflectionSession {
        require(wording.isNotBlank()) { "A confirmed conclusion cannot be blank." }

        database.withTransaction {
            val hypothesis = requireNotNull(knowledgeDao.getHypothesisById(hypothesisId)) {
                "Reflection proposal $hypothesisId does not exist."
            }
            check(hypothesis.status == ReflectionStatus.PROPOSED) {
                "Only a proposed reflection can be confirmed."
            }
            check(
                knowledgeDao.updateProposedHypothesisStatus(
                    hypothesisId,
                    ReflectionStatus.CONFIRMED
                ) == 1
            ) {
                "The reflection proposal changed before confirmation."
            }

            val createdAt = System.currentTimeMillis()
            val conclusionId = knowledgeDao.insertConclusion(
                ConclusionEntity(
                    rawRecordId = hypothesis.rawRecordId,
                    currentRevisionId = null,
                    createdAt = createdAt
                )
            )
            val revisionId = knowledgeDao.insertRevision(
                ConclusionRevisionEntity(
                    conclusionId = conclusionId,
                    version = 1,
                    text = wording,
                    author = "user",
                    createdAt = createdAt
                )
            )
            check(knowledgeDao.setCurrentRevision(conclusionId, revisionId) == 1)
            knowledgeDao.insertEvidenceLink(
                EvidenceLinkEntity(
                    conclusionRevisionId = revisionId,
                    sourceRawRecordId = hypothesis.rawRecordId,
                    relationship = "supports",
                    status = ReflectionStatus.CONFIRMED
                )
            )
        }

        return loadReflection(hypothesisId)
    }

    suspend fun reject(hypothesisId: Long): ReflectionSession {
        check(
            knowledgeDao.updateProposedHypothesisStatus(
                hypothesisId,
                ReflectionStatus.REJECTED
            ) == 1
        ) {
            "Only a proposed reflection can be rejected."
        }
        return loadReflection(hypothesisId)
    }
}
