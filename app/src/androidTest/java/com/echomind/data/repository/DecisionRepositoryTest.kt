package com.echomind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecisionRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun decisionChainIsInspectableFromQuestionToChoiceToOutcome() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val rawId = reflectionRepository.captureRawText(
                    "I keep going back and forth about changing roles."
                )
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(
                    proposal.hypothesisId,
                    "I should change roles for my long-term growth."
                )
                val revisionId = requireNotNull(session.revisionId)

                val decisionId = decisionRepository.createDecision(
                    question = "Should I change roles now?",
                    suggestion = "Change roles",
                    sourceRevisionId = revisionId
                )

                val created = decisionRepository.getDecision(decisionId)!!
                assertEquals("Should I change roles now?", created.question)
                assertEquals("Change roles", created.suggestion)
                assertEquals(revisionId, created.sourceRevisionId)
                assertEquals(
                    "I should change roles for my long-term growth.",
                    created.sourceConclusionText
                )
                assertNull(created.choice)
                assertFalse(created.hasOutcome)

                decisionRepository.setChoice(decisionId, "Change roles now")
                val decided = decisionRepository.getDecision(decisionId)!!
                assertTrue(decided.isDecided)
                assertEquals("Change roles now", decided.choice)

                val outcomeId = decisionRepository.recordOutcome(
                    decisionId,
                    "The move led to more freedom."
                )
                val withOutcome = decisionRepository.getDecision(decisionId)!!
                assertTrue(withOutcome.hasOutcome)
                assertEquals(1, withOutcome.outcomes.size)
                assertEquals("The move led to more freedom.", withOutcome.outcomes.single().report)

                assertTrue(decisionRepository.hasOutcomeForRevision(revisionId))

                decisionRepository.deleteDecision(decisionId)
                assertNull(decisionRepository.getDecision(decisionId))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun choiceIsRecordedOnlyOnce() {
        val database = inMemoryDatabase()
        try {
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val id = decisionRepository.createDecision("Which path?")
                decisionRepository.setChoice(id, "Path A")

                var rejected = false
                try {
                    decisionRepository.setChoice(id, "Path B")
                } catch (_: IllegalStateException) {
                    rejected = true
                }
                assertTrue(rejected)
                assertEquals("Path A", decisionRepository.getDecision(id)!!.choice)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingDecisionKeepsReferencedConclusionIntact() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val rawId = reflectionRepository.captureRawText("A decision source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "A conclusion")
                val revisionId = requireNotNull(session.revisionId)

                val decisionId = decisionRepository.createDecision(
                    question = "Q",
                    sourceRevisionId = revisionId
                )
                decisionRepository.recordOutcome(decisionId, "It went fine")

                decisionRepository.deleteDecision(decisionId)
                assertNull(decisionRepository.getDecision(decisionId))
                assertEquals(
                    "A conclusion",
                    database.knowledgeDao().getRevisionById(revisionId)?.text
                )
            }
        } finally {
            database.close()
        }
    }

    private fun reflectionRepository(database: AppDatabase) = ReflectionRepository(
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        analyzer = LocalReflectionAnalyzer(),
        json = Json { ignoreUnknownKeys = true }
    )

    private fun inMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DATABASE = "decision-repository-test.db"
    }
}
