package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionOutcome
import com.echomind.domain.model.DecisionSourceOption
import com.echomind.domain.model.OutcomeImpactReview
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionRepository @Inject constructor(
    private val database: AppDatabase,
    private val knowledgeDao: KnowledgeDao,
    private val reflectionRepository: ReflectionRepository
) {
    suspend fun createDecision(
        question: String,
        suggestion: String? = null,
        sourceRevisionId: Long? = null,
        suggestionAuthor: String? = null,
        suggestionSource: String? = null,
        suggestionStatus: String? = null
    ): Long {
        require(question.isNotBlank()) { "A decision needs a question." }
        if (!suggestion.isNullOrBlank()) {
            require(suggestionAuthor == "echomind") {
                "A system suggestion must identify EchoMind as its author."
            }
            require(!suggestionSource.isNullOrBlank()) {
                "A system suggestion needs source grounds."
            }
            require(suggestionStatus in setOf("proposal", "confirmed", "rejected")) {
                "A system suggestion needs a valid status."
            }
        }
        val groundedRevisionId = requireNotNull(sourceRevisionId) {
            "A decision must be grounded in a current conclusion revision."
        }
        return database.withTransaction {
            val revision = requireNotNull(knowledgeDao.getRevisionById(groundedRevisionId)) {
                "Source revision $groundedRevisionId does not exist."
            }
            val conclusion = requireNotNull(knowledgeDao.getConclusionById(revision.conclusionId)) {
                "Source revision $groundedRevisionId has no conclusion."
            }
            require(conclusion.currentRevisionId == groundedRevisionId) {
                "A decision must use the current revision as its grounds."
            }
            knowledgeDao.insertDecision(
                DecisionEntity(
                    question = question.trim(),
                    suggestion = suggestion?.trim()?.takeIf { it.isNotBlank() },
                    suggestionAuthor = suggestionAuthor,
                    suggestionSource = suggestionSource,
                    suggestionStatus = suggestionStatus,
                    choice = null,
                    sourceRevisionId = groundedRevisionId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun setChoice(decisionId: Long, choice: String) {
        require(choice.isNotBlank()) { "A choice cannot be blank." }
        database.withTransaction {
            requireNotNull(knowledgeDao.getDecisionById(decisionId)) { "Decision $decisionId missing." }
            check(
                knowledgeDao.setDecisionChoice(decisionId, choice.trim()) == 1
            ) { "Decision $decisionId already has a recorded choice." }
        }
    }

    suspend fun replaceChoice(decisionId: Long, choice: String) {
        require(choice.isNotBlank()) { "A choice cannot be blank." }
        database.withTransaction {
            requireNotNull(knowledgeDao.getDecisionById(decisionId)) { "Decision $decisionId missing." }
            check(knowledgeDao.replaceDecisionChoice(decisionId, choice.trim()) == 1) {
                "A choice cannot be replaced after an outcome has been reported."
            }
        }
    }

    suspend fun replaceGrounds(decisionId: Long, sourceRevisionId: Long) {
        database.withTransaction {
            requireCurrentRevision(sourceRevisionId)
            requireNotNull(knowledgeDao.getDecisionById(decisionId)) {
                "Decision $decisionId missing."
            }
            check(knowledgeDao.replaceDecisionGrounds(decisionId, sourceRevisionId) == 1) {
                "Decision grounds can only change before a choice is recorded."
            }
        }
    }

    suspend fun recordOutcome(decisionId: Long, report: String): Long {
        require(report.isNotBlank()) { "An outcome report cannot be blank." }
        return database.withTransaction {
            val decision = requireNotNull(knowledgeDao.getDecisionById(decisionId)) {
                "Decision $decisionId missing."
            }
            check(!decision.choice.isNullOrBlank()) {
                "Record a choice before reporting an outcome."
            }
            knowledgeDao.insertOutcome(
                OutcomeEntity(
                    decisionId = decisionId,
                    report = report.trim(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteDecision(decisionId: Long) {
        database.withTransaction {
            knowledgeDao.deleteOutcomesForDecision(decisionId)
            check(knowledgeDao.deleteDecisionById(decisionId) == 1) {
                "Decision $decisionId does not exist."
            }
        }
    }

    suspend fun deleteOutcome(decisionId: Long, outcomeId: Long) {
        database.withTransaction {
            check(knowledgeDao.deleteOutcome(decisionId, outcomeId) == 1) {
                "Outcome $outcomeId does not belong to decision $decisionId."
            }
        }
    }

    suspend fun getDecisions(): List<Decision> {
        val entities = knowledgeDao.getAllDecisions()
        if (entities.isEmpty()) return emptyList()
        val revisions = knowledgeDao.getRevisionsForDecisions().associateBy { it.id }
        val outcomesByDecision = knowledgeDao.getOutcomesForAllDecisions().groupBy { it.decisionId }
        return entities.map { entity ->
            toDomain(
                entity = entity,
                sourceText = entity.sourceRevisionId?.let { revisions[it]?.text },
                outcomes = outcomesByDecision[entity.id].orEmpty()
            )
        }
    }

    suspend fun getDecisionSources(): List<DecisionSourceOption> {
        val revisions = knowledgeDao.getAllRevisions().associateBy { it.id }
        return knowledgeDao.getAllConclusions()
            .mapNotNull { it.currentRevisionId?.let(revisions::get) }
            .sortedWith(compareByDescending<com.echomind.data.local.entity.ConclusionRevisionEntity> { it.createdAt }.thenBy { it.id })
            .map { DecisionSourceOption(it.id, it.version, it.text) }
    }

    suspend fun getDecision(decisionId: Long): Decision? =
        knowledgeDao.getDecisionById(decisionId)?.let { toDomain(it) }

    suspend fun getOutcomeImpact(decisionId: Long): OutcomeImpactReview? =
        database.withTransaction {
            val decision = requireNotNull(knowledgeDao.getDecisionById(decisionId)) {
                "Decision $decisionId missing."
            }
            val sourceRevisionId = decision.sourceRevisionId ?: return@withTransaction null
            val choice = decision.choice?.takeIf { it.isNotBlank() }
                ?: return@withTransaction null
            val outcomes = knowledgeDao.getOutcomesForDecision(decisionId)
            if (outcomes.isEmpty()) return@withTransaction null
            val sourceRevision = requireNotNull(knowledgeDao.getRevisionById(sourceRevisionId)) {
                "Source revision $sourceRevisionId does not exist."
            }
            val conclusion = requireNotNull(knowledgeDao.getConclusionById(sourceRevision.conclusionId)) {
                "Source revision $sourceRevisionId has no conclusion."
            }
            if (conclusion.currentRevisionId != sourceRevisionId) return@withTransaction null
            val originalText = sourceRevision.text
            val reports = outcomes.map { it.report }
            OutcomeImpactReview(
                decisionId = decisionId,
                sourceRevisionId = sourceRevisionId,
                originalText = originalText,
                choice = choice,
                outcomes = reports,
                proposedText = buildOutcomeImpactProposal(originalText, choice, reports)
            )
        }

    suspend fun applyOutcomeImpact(decisionId: Long, acceptedText: String): Long {
        require(acceptedText.isNotBlank()) { "A reviewed conclusion cannot be blank." }
        return database.withTransaction {
            val decision = requireNotNull(knowledgeDao.getDecisionById(decisionId)) {
                "Decision $decisionId missing."
            }
            check(!decision.choice.isNullOrBlank()) {
                "Record a choice before reviewing its outcome."
            }
            check(knowledgeDao.getOutcomesForDecision(decisionId).isNotEmpty()) {
                "Report an outcome before reviewing its impact."
            }
            val sourceRevisionId = requireNotNull(decision.sourceRevisionId) {
                "A decision must be grounded in a conclusion revision."
            }
            reflectionRepository.reviseCurrentConclusionInTransaction(
                sourceRevisionId = sourceRevisionId,
                newWording = acceptedText.trim()
            )
        }
    }

    suspend fun hasOutcomeForRevision(revisionId: Long): Boolean {
        val decisionIds = knowledgeDao
            .getDecisionsForSourceRevision(revisionId)
            .map { it.id }
        return decisionIds.any { id ->
            knowledgeDao.getOutcomesForDecision(id).isNotEmpty()
        }
    }

    private suspend fun toDomain(entity: DecisionEntity): Decision = toDomain(
        entity = entity,
        sourceText = entity.sourceRevisionId?.let { knowledgeDao.getRevisionById(it)?.text },
        outcomes = knowledgeDao.getOutcomesForDecision(entity.id)
    )

    private fun toDomain(
        entity: DecisionEntity,
        sourceText: String?,
        outcomes: List<OutcomeEntity>
    ): Decision {
        return Decision(
            id = entity.id,
            question = entity.question,
            suggestion = entity.suggestion,
            suggestionAuthor = entity.suggestionAuthor,
            suggestionSource = entity.suggestionSource,
            suggestionStatus = entity.suggestionStatus,
            choice = entity.choice,
            sourceRevisionId = entity.sourceRevisionId,
            sourceConclusionText = sourceText,
            createdAt = entity.createdAt,
            outcomes = outcomes.map {
                DecisionOutcome(it.id, it.decisionId, it.report, it.createdAt)
            }
        )
    }

    private suspend fun requireCurrentRevision(revisionId: Long) {
        val revision = requireNotNull(knowledgeDao.getRevisionById(revisionId)) {
            "Source revision $revisionId does not exist."
        }
        val conclusion = requireNotNull(knowledgeDao.getConclusionById(revision.conclusionId)) {
            "Source revision $revisionId has no conclusion."
        }
        require(conclusion.currentRevisionId == revisionId) {
            "Decision grounds must use the current conclusion revision."
        }
    }

    private fun buildOutcomeImpactProposal(
        originalText: String,
        choice: String,
        outcomes: List<String>
    ): String = buildString {
        append(originalText.trim())
        append("\nOutcome after choosing \"")
        append(choice.trim())
        append("\": ")
        append(outcomes.joinToString(" ") { it.trim() })
    }
}
