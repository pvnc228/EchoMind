package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import com.echomind.domain.model.DecisionDeletionDependency
import com.echomind.domain.model.EntryDeletionChoice
import com.echomind.domain.model.EntryDeletionPlan
import com.echomind.domain.model.EvidenceDeletionDependency
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val knowledgeDao: KnowledgeDao
) {
    fun getAllEntries(): Flow<List<Entry>> =
        entryDao.getAllEntries().map { entities -> entities.map { it.toDomain() } }

    fun getEntriesByCategory(category: String): Flow<List<Entry>> =
        entryDao.getEntriesByCategory(category).map { entities -> entities.map { it.toDomain() } }

    fun searchEntries(query: String): Flow<List<Entry>> =
        entryDao.searchEntries(query).map { entities -> entities.map { it.toDomain() } }

    suspend fun getEntryById(id: Long): Entry? =
        entryDao.getEntryById(id)?.toDomain()

    suspend fun saveEntry(entry: Entry) {
        if (entry.id == 0L) {
            database.withTransaction {
                val entryId = entryDao.insertEntry(entry.toEntity())
                knowledgeDao.insertRawRecord(entry.toRawRecordEntity(entryId))
            }
        } else {
            entryDao.updateEntry(entry.toEntity())
        }
    }

    suspend fun getDeletionPlan(id: Long): EntryDeletionPlan? {
        val entry = entryDao.getEntryById(id) ?: return null
        val rawRecord = knowledgeDao.getRawRecordByLegacyEntryId(id) ?: return null
        val conclusion = knowledgeDao.getConclusionForRawRecord(rawRecord.id)
        val revisions = conclusion?.let { knowledgeDao.getRevisionsForConclusion(it.id) }.orEmpty()
        val revisionIds = revisions.map { it.id }
        val ownConclusionId = conclusion?.id
        val incomingEvidence = knowledgeDao.getIncomingEvidenceLinks(rawRecord.id)
            .mapNotNull { link ->
                val revision = revisions.firstOrNull { it.id == link.conclusionRevisionId }
                    ?: knowledgeDao.getRevisionById(link.conclusionRevisionId)
                    ?: return@mapNotNull null
                val linkConclusion = knowledgeDao.getConclusionById(revision.conclusionId)
                if (linkConclusion?.id == ownConclusionId) return@mapNotNull null
                EvidenceDeletionDependency(link.id, link.conclusionRevisionId, link.relationship)
            }
        val decisions = revisionIds.flatMap { revisionId ->
            knowledgeDao.getDecisionsForSourceRevision(revisionId).map { decision ->
                DecisionDeletionDependency(
                    decisionId = decision.id,
                    sourceRevisionId = revisionId,
                    outcomeCount = knowledgeDao.getOutcomesForDecision(decision.id).size
                )
            }
        }.distinctBy { it.decisionId }
        return EntryDeletionPlan(
            entryId = entry.id,
            rawRecordId = rawRecord.id,
            ownConclusionId = ownConclusionId,
            revisionIds = revisionIds,
            incomingEvidence = incomingEvidence,
            decisions = decisions,
            audioPath = entry.audioPath
        )
    }

    suspend fun deleteEntry(id: Long, includeConfirmedConclusion: Boolean = false) {
        val plan = getDeletionPlan(id) ?: return
        deleteEntry(
            id,
            EntryDeletionChoice(
                deleteOwnConclusion = includeConfirmedConclusion,
                unlinkIncomingEvidenceLinkIds = if (includeConfirmedConclusion) {
                    plan.incomingEvidence.map { it.linkId }.toSet()
                } else {
                    emptySet()
                },
                deleteDecisionIds = if (includeConfirmedConclusion) plan.decisions.map { it.decisionId }.toSet() else emptySet()
            )
        )
    }

    suspend fun deleteEntry(id: Long, choice: EntryDeletionChoice) {
        val plan = getDeletionPlan(id) ?: return
        if (plan.ownConclusionId != null && !choice.deleteOwnConclusion) {
            throw ConfirmedConclusionDeletionRequiredException()
        }
        val unresolvedLinks = plan.incomingEvidence
            .map { it.linkId }
            .filterNot { it in choice.unlinkIncomingEvidenceLinkIds }
        if (unresolvedLinks.isNotEmpty()) {
            throw DeletionDependenciesRequireExplicitChoiceException(
                "Choose unlink for incoming evidence links: ${unresolvedLinks.joinToString()}"
            )
        }
        val unresolvedDecisions = plan.decisions
            .map { it.decisionId }
            .filterNot { it in choice.deleteDecisionIds }
        if (unresolvedDecisions.isNotEmpty()) {
            throw DeletionDependenciesRequireExplicitChoiceException(
                "Choose delete or cancel for decisions: ${unresolvedDecisions.joinToString()}"
            )
        }

        database.withTransaction {
            choice.deleteDecisionIds.forEach { decisionId ->
                knowledgeDao.deleteOutcomesForDecision(decisionId)
                knowledgeDao.deleteDecisionById(decisionId)
            }
            choice.unlinkIncomingEvidenceLinkIds.forEach { linkId ->
                knowledgeDao.deleteEvidenceLinkById(linkId)
            }
            if (choice.deleteOwnConclusion) {
                val conclusion = knowledgeDao.getConclusionById(plan.ownConclusionId!!)
                if (conclusion != null) knowledgeDao.deleteConclusion(conclusion)
            }
            knowledgeDao.deleteRawRecordByLegacyEntryId(id)
            entryDao.deleteEntryById(id)
        }
        // DB commit precedes filesystem cleanup; callers must surface a partial cleanup failure.
        plan.audioPath?.let { path ->
            val audioFile = File(path)
            if (audioFile.exists() && !audioFile.delete()) {
                throw AudioDeletionFailedException(path)
            }
        }
    }

    suspend fun getRecentEntries(limit: Int): List<Entry> =
        entryDao.getRecentEntries(limit).map { it.toDomain() }

    private fun EntryEntity.toDomain() = Entry(
        id = id,
        transcript = transcript,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAt = createdAt,
        category = EntryCategory.fromString(category),
        tags = tags,
        summary = summary,
        tasks = tasks,
        ideas = ideas,
        emotions = emotions
    )

    private fun Entry.toEntity() = EntryEntity(
        id = id,
        transcript = transcript,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAt = createdAt,
        category = category.name.lowercase(),
        tags = tags,
        summary = summary,
        tasks = tasks,
        ideas = ideas,
        emotions = emotions
    )

    private fun Entry.toRawRecordEntity(entryId: Long) = RawRecordEntity(
        legacyEntryId = entryId,
        originalText = transcript,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAt = createdAt
    )
}

class ConfirmedConclusionDeletionRequiredException : IllegalStateException(
    "This source supports a confirmed conclusion. Delete the conclusion and source together, or cancel."
)

class AudioDeletionFailedException(path: String) : IllegalStateException(
    "The database records were deleted, but the attached audio could not be removed: $path"
)

class DeletionDependenciesRequireExplicitChoiceException(message: String) : IllegalStateException(message)
