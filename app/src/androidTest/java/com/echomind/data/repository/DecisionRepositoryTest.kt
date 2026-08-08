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
import org.junit.Assert.assertThrows
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
                    sourceRevisionId = revisionId,
                    suggestionAuthor = "echomind",
                    suggestionSource = revisionId.toString(),
                    suggestionStatus = "proposal"
                )

                val created = decisionRepository.getDecision(decisionId)!!
                assertEquals("Should I change roles now?", created.question)
                assertEquals("Change roles", created.suggestion)
                assertEquals("echomind", created.suggestionAuthor)
                assertEquals(revisionId.toString(), created.suggestionSource)
                assertEquals("proposal", created.suggestionStatus)
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
                val id = decisionRepository.createDecision(
                    "Which path?",
                    sourceRevisionId = currentRevision(database)
                )
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
    fun choiceCanBeReplacedBeforeOutcomeButNotAfterOutcome() {
        val database = inMemoryDatabase()
        try {
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val id = decisionRepository.createDecision(
                    "Which path?",
                    sourceRevisionId = currentRevision(database)
                )
                decisionRepository.setChoice(id, "Path A")
                decisionRepository.replaceChoice(id, "Path B")
                assertEquals("Path B", decisionRepository.getDecision(id)!!.choice)
                decisionRepository.recordOutcome(id, "Observed result")
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { decisionRepository.replaceChoice(id, "Path C") }
                }
                assertEquals("Path B", decisionRepository.getDecision(id)!!.choice)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun outcomeCannotBeRecordedBeforeChoice() {
        val database = inMemoryDatabase()
        try {
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val decisionId = decisionRepository.createDecision(
                    "Which path?",
                    sourceRevisionId = currentRevision(database)
                )

                assertThrows(IllegalStateException::class.java) {
                    runBlocking { decisionRepository.recordOutcome(decisionId, "Outcome too early") }
                }
                assertTrue(decisionRepository.getDecision(decisionId)!!.outcomes.isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun concreteOutcomeCanBeRemovedWithoutDeletingTheDecision() {
        val database = inMemoryDatabase()
        try {
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val decisionId = decisionRepository.createDecision(
                    "Which path?",
                    sourceRevisionId = currentRevision(database)
                )
                decisionRepository.setChoice(decisionId, "Path A")
                val outcomeId = decisionRepository.recordOutcome(decisionId, "Observed result")

                decisionRepository.deleteOutcome(decisionId, outcomeId)

                val decision = requireNotNull(decisionRepository.getDecision(decisionId))
                assertTrue(decision.outcomes.isEmpty())
                assertFalse(decision.hasOutcome)
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { decisionRepository.deleteOutcome(decisionId, outcomeId) }
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun newDecisionRequiresTheCurrentRevisionAsGrounds() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val decisionRepository = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { decisionRepository.createDecision("Missing grounds") }
                }
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        decisionRepository.createDecision("Dangling grounds", sourceRevisionId = 9999L)
                    }
                }

                val rawId = reflectionRepository.captureRawText("A revisable conclusion")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val first = reflectionRepository.confirm(proposal.hypothesisId, "First wording")
                val historicalRevisionId = requireNotNull(first.revisionId)
                val revised = reflectionRepository.revise(proposal.hypothesisId, "Current wording")
                val currentRevisionId = requireNotNull(revised.revisionId)

                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        decisionRepository.createDecision(
                            "Historical grounds",
                            sourceRevisionId = historicalRevisionId
                        )
                    }
                }
                val created = decisionRepository.createDecision(
                    "Current grounds",
                    sourceRevisionId = currentRevisionId
                )
                assertEquals(currentRevisionId, decisionRepository.getDecision(created)?.sourceRevisionId)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun groundsCanBeReplacedOnlyBeforeChoice() {
        val database = inMemoryDatabase()
        try {
            val reflection = reflectionRepository(database)
            val decisions = DecisionRepository(database, database.knowledgeDao())
            runBlocking {
                val firstRaw = reflection.captureRawText("First grounds")
                val firstProposal = reflection.createLocalProposal(firstRaw)
                val firstRevision = requireNotNull(
                    reflection.confirm(firstProposal.hypothesisId, "First conclusion").revisionId
                )
                val secondRaw = reflection.captureRawText("Second grounds")
                val secondProposal = reflection.createLocalProposal(secondRaw)
                val secondRevision = requireNotNull(
                    reflection.confirm(secondProposal.hypothesisId, "Second conclusion").revisionId
                )
                val decisionId = decisions.createDecision("Which grounds?", sourceRevisionId = firstRevision)

                decisions.replaceGrounds(decisionId, secondRevision)
                assertEquals(secondRevision, decisions.getDecision(decisionId)?.sourceRevisionId)
                decisions.setChoice(decisionId, "Proceed")
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { decisions.replaceGrounds(decisionId, firstRevision) }
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun databaseRejectsDecisionWithMissingSourceRevision() {
        val database = inMemoryDatabase()
        try {
            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking {
                    database.knowledgeDao().insertDecision(
                        com.echomind.data.local.entity.DecisionEntity(
                            question = "Missing source",
                            sourceRevisionId = 9999L,
                            createdAt = 1L
                        )
                    )
                }
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
                decisionRepository.setChoice(decisionId, "Proceed")
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

    private suspend fun currentRevision(database: AppDatabase): Long {
        val reflection = reflectionRepository(database)
        val rawId = reflection.captureRawText("Decision test grounds")
        val proposal = reflection.createLocalProposal(rawId)
        return requireNotNull(reflection.confirm(proposal.hypothesisId, "Decision grounds").revisionId)
    }

    private fun inMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DATABASE = "decision-repository-test.db"
    }
}
