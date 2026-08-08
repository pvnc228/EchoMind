package com.echomind.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.AudioCleanupEntity
import com.echomind.data.cleanup.AudioCleanupScheduler
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import com.echomind.domain.model.DecisionDeletionDependency
import com.echomind.domain.model.EntryDeletionChoice
import com.echomind.domain.model.EntryDeletionPlan
import com.echomind.domain.model.EvidenceDeletionDependency
import com.echomind.domain.model.ReflectionProposalDeletionDependency
import com.echomind.domain.model.ThemeLinkDeletionDependency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val knowledgeDao: KnowledgeDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val MAX_AUDIO_CLEANUP_BATCH = 32
        const val MAX_AUDIO_CLEANUP_ATTEMPTS = 8
    }

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
        return database.withTransaction { buildDeletionPlan(id) }
    }

    private suspend fun buildDeletionPlan(id: Long): EntryDeletionPlan? {
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
        val themeLinks = revisionIds.flatMap { revisionId ->
            knowledgeDao.getThemeLinksForRevision(revisionId).mapNotNull { link ->
                val theme = knowledgeDao.getThemeById(link.themeId) ?: return@mapNotNull null
                ThemeLinkDeletionDependency(
                    linkId = link.id,
                    themeId = theme.id,
                    themeName = theme.name,
                    revisionId = revisionId,
                    confirmed = link.confirmed
                )
            }
        }.distinctBy { it.linkId }
        val proposals = knowledgeDao.getHypothesesForRawRecord(rawRecord.id)
            .map { ReflectionProposalDeletionDependency(it.id, it.status) }
        return EntryDeletionPlan(
            entryId = entry.id,
            rawRecordId = rawRecord.id,
            ownConclusionId = ownConclusionId,
            revisionIds = revisionIds,
            incomingEvidence = incomingEvidence,
            decisions = decisions,
            audioPath = entry.audioPath,
            themeLinks = themeLinks,
            proposals = proposals
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
        val deletedPlan = database.withTransaction {
            val plan = buildDeletionPlan(id) ?: return@withTransaction null
            validateDeletionChoice(plan, choice)
            choice.deleteDecisionIds.forEach { decisionId ->
                val dependency = plan.decisions.first { it.decisionId == decisionId }
                check(
                    knowledgeDao.deleteOutcomesForDecision(decisionId) == dependency.outcomeCount
                ) { "Decision $decisionId changed before deletion." }
                check(knowledgeDao.deleteDecisionById(decisionId) == 1) {
                    "Decision $decisionId changed before deletion."
                }
            }
            choice.unlinkIncomingEvidenceLinkIds.forEach { linkId ->
                check(knowledgeDao.deleteEvidenceLinkById(linkId) == 1) {
                    "Evidence link $linkId changed before deletion."
                }
            }
            if (choice.deleteOwnConclusion) {
                val conclusion = knowledgeDao.getConclusionById(plan.ownConclusionId!!)
                check(conclusion != null) { "Conclusion ${plan.ownConclusionId} changed before deletion." }
                knowledgeDao.deleteConclusion(conclusion)
            }
            check(knowledgeDao.deleteRawRecordByLegacyEntryId(id) == 1) {
                "Raw record for entry $id changed before deletion."
            }
            check(entryDao.deleteEntryById(id) == 1) { "Entry $id changed before deletion." }
            plan
        } ?: return
        // DB commit precedes filesystem cleanup; persist a bounded retry state for failures.
        deletedPlan.audioPath?.let { path ->
            if (!deleteAudioFile(path)) {
                rememberAudioCleanupFailure(path, deletedPlan.entryId)
                AudioCleanupScheduler.enqueue(context)
                throw AudioDeletionFailedException(path)
            }
            knowledgeDao.deleteAudioCleanup(path)
        }
    }

    suspend fun getPendingAudioCleanup(
        limit: Int = MAX_AUDIO_CLEANUP_BATCH
    ): List<AudioCleanupEntity> {
        require(limit in 1..MAX_AUDIO_CLEANUP_BATCH) {
            "Audio cleanup batch must be between 1 and $MAX_AUDIO_CLEANUP_BATCH."
        }
        return knowledgeDao.getPendingAudioCleanup(limit)
    }

    suspend fun getPendingAudioCleanupCount(): Int = knowledgeDao.getAudioCleanupCount()

    suspend fun retryPendingAudioCleanup(limit: Int = MAX_AUDIO_CLEANUP_BATCH): Int {
        val pending = getPendingAudioCleanup(limit)
        var cleaned = 0
        pending.forEach { cleanup ->
            if (deleteAudioFile(cleanup.path)) {
                knowledgeDao.deleteAudioCleanup(cleanup.path)
                cleaned += 1
            } else {
                knowledgeDao.upsertAudioCleanup(
                    cleanup.copy(
                        failedAt = System.currentTimeMillis(),
                        attemptCount = cleanup.attemptCount + 1
                    )
                )
            }
        }
        return cleaned
    }

    private suspend fun rememberAudioCleanupFailure(path: String, entryId: Long) {
        val previous = knowledgeDao.getAudioCleanup(path)
        knowledgeDao.upsertAudioCleanup(
            AudioCleanupEntity(
                path = path,
                entryId = entryId,
                failedAt = System.currentTimeMillis(),
                attemptCount = (previous?.attemptCount ?: 0) + 1
            )
        )
    }

    private fun deleteAudioFile(path: String): Boolean {
        if (!isAppOwnedAudioPath(path)) return false
        val audioFile = File(path)
        if (!audioFile.exists()) return true
        return audioFile.isFile && audioFile.delete()
    }

    private fun validateDeletionChoice(plan: EntryDeletionPlan, choice: EntryDeletionChoice) {
        require(choice.unlinkIncomingEvidenceLinkIds.all { linkId ->
            plan.incomingEvidence.any { it.linkId == linkId }
        }) { "Deletion choice contains an evidence link outside the deletion plan." }
        require(choice.deleteDecisionIds.all { decisionId ->
            plan.decisions.any { it.decisionId == decisionId }
        }) { "Deletion choice contains a decision outside the deletion plan." }
        if (plan.ownConclusionId != null && !choice.deleteOwnConclusion) {
            throw ConfirmedConclusionDeletionRequiredException()
        }
        require(!choice.deleteOwnConclusion || plan.ownConclusionId != null) {
            "Deletion choice requests a conclusion outside the deletion plan."
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
    }

    private fun isAppOwnedAudioPath(path: String): Boolean {
        val canonical = File(path).canonicalFile
        val roots = listOfNotNull(
            context.filesDir,
            context.noBackupFilesDir,
            context.cacheDir,
            context.getExternalFilesDir(null)
        ).map(File::getCanonicalFile)
        return roots.any { root ->
            canonical == root || canonical.path.startsWith(root.path + File.separator)
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
