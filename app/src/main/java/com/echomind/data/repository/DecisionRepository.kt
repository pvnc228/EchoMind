package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionOutcome
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecisionRepository @Inject constructor(
    private val database: AppDatabase,
    private val knowledgeDao: KnowledgeDao
) {
    suspend fun createDecision(
        question: String,
        suggestion: String? = null,
        sourceRevisionId: Long? = null
    ): Long {
        require(question.isNotBlank()) { "A decision needs a question." }
        if (sourceRevisionId != null) {
            requireNotNull(knowledgeDao.getRevisionById(sourceRevisionId)) {
                "Source revision $sourceRevisionId does not exist."
            }
        }
        return knowledgeDao.insertDecision(
            DecisionEntity(
                question = question.trim(),
                suggestion = suggestion?.trim()?.takeIf { it.isNotBlank() },
                choice = null,
                sourceRevisionId = sourceRevisionId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setChoice(decisionId: Long, choice: String) {
        require(choice.isNotBlank()) { "A choice cannot be blank." }
        requireNotNull(knowledgeDao.getDecisionById(decisionId)) { "Decision $decisionId missing." }
        check(
            knowledgeDao.setDecisionChoice(decisionId, choice.trim()) == 1
        ) { "Decision $decisionId already has a recorded choice." }
    }

    suspend fun recordOutcome(decisionId: Long, report: String): Long {
        require(report.isNotBlank()) { "An outcome report cannot be blank." }
        requireNotNull(knowledgeDao.getDecisionById(decisionId)) { "Decision $decisionId missing." }
        return knowledgeDao.insertOutcome(
            OutcomeEntity(
                decisionId = decisionId,
                report = report.trim(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDecision(decisionId: Long) {
        database.withTransaction {
            knowledgeDao.deleteOutcomesForDecision(decisionId)
            check(knowledgeDao.deleteDecisionById(decisionId) == 1) {
                "Decision $decisionId does not exist."
            }
        }
    }

    suspend fun getDecisions(): List<Decision> =
        knowledgeDao.getAllDecisions().map { toDomain(it) }

    suspend fun getDecision(decisionId: Long): Decision? =
        knowledgeDao.getDecisionById(decisionId)?.let { toDomain(it) }

    suspend fun hasOutcomeForRevision(revisionId: Long): Boolean {
        val decisionIds = knowledgeDao
            .getDecisionsForSourceRevision(revisionId)
            .map { it.id }
        return decisionIds.any { id ->
            knowledgeDao.getOutcomesForDecision(id).isNotEmpty()
        }
    }

    private suspend fun toDomain(entity: DecisionEntity): Decision {
        val sourceText = entity.sourceRevisionId?.let {
            knowledgeDao.getRevisionById(it)?.text
        }
        return Decision(
            id = entity.id,
            question = entity.question,
            suggestion = entity.suggestion,
            choice = entity.choice,
            sourceRevisionId = entity.sourceRevisionId,
            sourceConclusionText = sourceText,
            createdAt = entity.createdAt,
            outcomes = knowledgeDao.getOutcomesForDecision(entity.id).map {
                DecisionOutcome(it.id, it.decisionId, it.report, it.createdAt)
            }
        )
    }
}
