package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.CaptureDraftEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.ReflectionStatus
import com.echomind.domain.model.Revision
import com.echomind.domain.model.CaptureDraft
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
            captureRawTextInTransaction(originalText, audioPath, durationMs, createdAt)
        }
    }

    suspend fun submitCaptureDraft(
        originalText: String,
        audioPath: String? = null,
        durationMs: Long = 0
    ): Long {
        require(originalText.isNotBlank()) { "A reflection cannot be blank." }
        val createdAt = System.currentTimeMillis()
        return database.withTransaction {
            val rawRecordId = captureRawTextInTransaction(
                originalText,
                audioPath,
                durationMs,
                createdAt
            )
            knowledgeDao.deleteCaptureDraft()
            rawRecordId
        }
    }

    suspend fun saveCaptureDraft(
        text: String,
        encryptedAudioPath: String? = null,
        durationMs: Long = 0L,
        captureStage: String = "CAPTURE"
    ) {
        require(captureStage != "RECORDING") { "Transient recording stage cannot be persisted." }
        val now = System.currentTimeMillis()
        database.withTransaction {
            if (text.isBlank() && encryptedAudioPath == null) {
                knowledgeDao.deleteCaptureDraft()
            } else {
                val existing = knowledgeDao.getCaptureDraft()
                knowledgeDao.upsertCaptureDraft(
                    CaptureDraftEntity(
                        id = 1L,
                        text = text,
                        encryptedAudioPath = encryptedAudioPath,
                        durationMs = durationMs,
                        captureStage = captureStage,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    suspend fun loadCaptureDraft(): CaptureDraft? =
        knowledgeDao.getCaptureDraft()?.let { draft ->
            CaptureDraft(
                id = draft.id,
                text = draft.text,
                encryptedAudioPath = draft.encryptedAudioPath,
                durationMs = draft.durationMs,
                captureStage = draft.captureStage,
                createdAt = draft.createdAt,
                updatedAt = draft.updatedAt
            )
        }

    suspend fun clearCaptureDraft() {
        knowledgeDao.deleteCaptureDraft()
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
                    status = ReflectionStatus.CONFIRMED,
                    origin = "intrinsic_source",
                    createdAt = createdAt
                )
            )
        }

        return loadReflection(hypothesisId)
    }

    suspend fun getRevisionHistory(hypothesisId: Long): List<Revision> {
        val hypothesis = requireNotNull(
            knowledgeDao.getHypothesisById(hypothesisId)
        ) { "Reflection proposal $hypothesisId does not exist." }
        val conclusion = knowledgeDao.getConclusionForRawRecord(hypothesis.rawRecordId)
            ?: return emptyList()
        val currentRevisionId = conclusion.currentRevisionId
        return knowledgeDao.getRevisionsForConclusion(conclusion.id).map { entity ->
            Revision(
                version = entity.version,
                text = entity.text,
                author = entity.author,
                createdAt = entity.createdAt,
                isCurrent = entity.id == currentRevisionId
            )
        }
    }

    suspend fun getCurrentRevisionId(hypothesisId: Long): Long? {
        val hypothesis = requireNotNull(
            knowledgeDao.getHypothesisById(hypothesisId)
        ) { "Reflection proposal $hypothesisId does not exist." }
        return knowledgeDao.getConclusionForRawRecord(hypothesis.rawRecordId)?.currentRevisionId
    }

    suspend fun revise(hypothesisId: Long, newWording: String): ReflectionSession {
        require(newWording.isNotBlank()) { "A revised conclusion cannot be blank." }

        database.withTransaction {
            val hypothesis = requireNotNull(knowledgeDao.getHypothesisById(hypothesisId)) {
                "Reflection proposal $hypothesisId does not exist."
            }
            check(hypothesis.status == ReflectionStatus.CONFIRMED) {
                "Only a confirmed reflection can be revised."
            }
            val conclusion = requireNotNull(
                knowledgeDao.getConclusionForRawRecord(hypothesis.rawRecordId)
            ) { "No confirmed conclusion to revise." }
            val currentRevision = conclusion.currentRevisionId?.let { revisionId ->
                requireNotNull(knowledgeDao.getRevisionById(revisionId)) {
                    "Current revision $revisionId does not exist."
                }
            } ?: throw IllegalStateException("No current revision to revise.")

            appendRevisionInTransaction(conclusion, currentRevision, newWording)
        }

        return loadReflection(hypothesisId)
    }

    internal suspend fun reviseCurrentConclusionInTransaction(
        sourceRevisionId: Long,
        newWording: String
    ): Long {
        require(newWording.isNotBlank()) { "A revised conclusion cannot be blank." }
        val currentRevision = requireNotNull(knowledgeDao.getRevisionById(sourceRevisionId)) {
            "Source revision $sourceRevisionId does not exist."
        }
        val conclusion = requireNotNull(knowledgeDao.getConclusionById(currentRevision.conclusionId)) {
            "Source revision $sourceRevisionId has no conclusion."
        }
        check(conclusion.currentRevisionId == sourceRevisionId) {
            "The decision grounds changed before review confirmation."
        }
        return appendRevisionInTransaction(conclusion, currentRevision, newWording)
    }

    private suspend fun appendRevisionInTransaction(
        conclusion: ConclusionEntity,
        currentRevision: ConclusionRevisionEntity,
        newWording: String
    ): Long {
        val createdAt = System.currentTimeMillis()
        val newRevisionId = knowledgeDao.insertRevision(
            ConclusionRevisionEntity(
                conclusionId = conclusion.id,
                version = (knowledgeDao.getMaxRevisionVersion(conclusion.id) ?: currentRevision.version) + 1,
                text = newWording,
                author = "user",
                createdAt = createdAt
            )
        )
        val now = System.currentTimeMillis()
        knowledgeDao.insertEvidenceLink(
            EvidenceLinkEntity(
                conclusionRevisionId = newRevisionId,
                sourceRawRecordId = conclusion.rawRecordId,
                relationship = "supports",
                status = ReflectionStatus.CONFIRMED,
                origin = "intrinsic_source",
                createdAt = now
            )
        )
        knowledgeDao.getEvidenceLinksForRevision(currentRevision.id)
            .filter { it.sourceRawRecordId != conclusion.rawRecordId }
            .forEach { link ->
                knowledgeDao.insertEvidenceLink(
                    link.copy(
                        id = 0,
                        conclusionRevisionId = newRevisionId,
                        status = "needs_review",
                        origin = "proposed_inherited",
                        createdAt = now,
                        createdAtEstimated = false,
                        reviewMetadata = "inherited_from_revision=${currentRevision.id}"
                    )
                )
            }
        knowledgeDao.getThemeLinksForRevision(currentRevision.id).forEach { link ->
            knowledgeDao.insertThemeLink(
                link.copy(
                    id = 0,
                    conclusionRevisionId = newRevisionId,
                    confirmed = false,
                    origin = "proposed_inherited",
                    reviewRequired = true,
                    createdAt = now
                )
            )
        }
        check(knowledgeDao.setCurrentRevision(conclusion.id, newRevisionId) == 1)
        return newRevisionId
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

    private suspend fun captureRawTextInTransaction(
        originalText: String,
        audioPath: String?,
        durationMs: Long,
        createdAt: Long
    ): Long {
        val entryId = entryDao.insertEntry(
            EntryEntity(
                transcript = originalText,
                audioPath = audioPath,
                durationMs = durationMs,
                createdAt = createdAt
            )
        )
        return knowledgeDao.insertRawRecord(
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
