package com.echomind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.data.local.entity.RawRecordEntity
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
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

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
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository)
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
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository(database))
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
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository(database))
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
    fun decisionChoiceAndOutcomeSurviveDatabaseRestart() {
        val firstDatabase = fileDatabase()
        val decisionId: Long
        try {
            val reflection = reflectionRepository(firstDatabase)
            val decisions = DecisionRepository(
                firstDatabase,
                firstDatabase.knowledgeDao(),
                reflection
            )
            decisionId = runBlocking {
                val revisionId = currentRevision(firstDatabase)
                val createdId = decisions.createDecision(
                    question = "Which path survives a restart?",
                    sourceRevisionId = revisionId
                )
                decisions.setChoice(createdId, "Keep the selected path")
                decisions.recordOutcome(createdId, "The selected path remained valid.")
                createdId
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val restored = runBlocking {
                DecisionRepository(
                    reopenedDatabase,
                    reopenedDatabase.knowledgeDao(),
                    reflectionRepository(reopenedDatabase)
                ).getDecision(decisionId)
            }

            assertEquals("Which path survives a restart?", restored?.question)
            assertEquals("Keep the selected path", restored?.choice)
            assertEquals(1, restored?.outcomes?.size)
            assertEquals(
                "The selected path remained valid.",
                restored?.outcomes?.single()?.report
            )
            assertTrue(restored?.sourceRevisionId != null)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun outcomeCannotBeRecordedBeforeChoice() {
        val database = inMemoryDatabase()
        try {
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository(database))
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
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository(database))
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
    fun outcomeImpactReviewShowsOriginalGroundsAndDoesNotCreateRevision() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("A decision source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val originalRevisionId = requireNotNull(
                    reflectionRepository.confirm(proposal.hypothesisId, "The original conclusion").revisionId
                )
                val decisionId = decisionRepository.createDecision(
                    question = "Should I continue?",
                    sourceRevisionId = originalRevisionId
                )
                decisionRepository.setChoice(decisionId, "Continue")
                decisionRepository.recordOutcome(decisionId, "The result was better than expected.")

                val review = requireNotNull(decisionRepository.getOutcomeImpact(decisionId))

                assertEquals(decisionId, review.decisionId)
                assertEquals(originalRevisionId, review.sourceRevisionId)
                assertEquals("The original conclusion", review.originalText)
                assertEquals("Continue", review.choice)
                assertEquals(
                    listOf("The result was better than expected."),
                    review.outcomes
                )
                assertEquals(
                    "The original conclusion\nOutcome after choosing \"Continue\": " +
                        "The result was better than expected.",
                    review.proposedText
                )
                assertEquals(
                    originalRevisionId,
                    database.knowledgeDao().getAllConclusions().single().currentRevisionId
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun confirmingOutcomeImpactAppendsRevisionWithoutRewritingDecisionGrounds() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val decisionRepository = DecisionRepository(
                database,
                database.knowledgeDao(),
                reflectionRepository
            )
            runBlocking {
                val rawId = reflectionRepository.captureRawText("A decision source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val originalRevisionId = requireNotNull(
                    reflectionRepository.confirm(proposal.hypothesisId, "The original conclusion").revisionId
                )
                val decisionId = decisionRepository.createDecision(
                    question = "Should I continue?",
                    sourceRevisionId = originalRevisionId
                )
                decisionRepository.setChoice(decisionId, "Continue")
                decisionRepository.recordOutcome(decisionId, "The result was better than expected.")

                val newRevisionId = decisionRepository.applyOutcomeImpact(
                    decisionId,
                    "The outcome supports continuing with the same approach."
                )

                assertTrue(newRevisionId != originalRevisionId)
                assertEquals(
                    "The original conclusion",
                    database.knowledgeDao().getRevisionById(originalRevisionId)?.text
                )
                assertEquals(
                    "The outcome supports continuing with the same approach.",
                    database.knowledgeDao().getRevisionById(newRevisionId)?.text
                )
                assertEquals(
                    newRevisionId,
                    database.knowledgeDao().getAllConclusions().single().currentRevisionId
                )
                assertEquals(
                    originalRevisionId,
                    decisionRepository.getDecision(decisionId)?.sourceRevisionId
                )
                assertNull(decisionRepository.getOutcomeImpact(decisionId))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun staleOutcomeImpactCannotApplyAfterGroundsChange() {
        val database = inMemoryDatabase()
        try {
            val reflection = reflectionRepository(database)
            val decisions = DecisionRepository(database, database.knowledgeDao(), reflection)
            runBlocking {
                val rawId = reflection.captureRawText("A decision source")
                val proposal = reflection.createLocalProposal(rawId)
                val originalRevisionId = requireNotNull(
                    reflection.confirm(proposal.hypothesisId, "The original conclusion").revisionId
                )
                val decisionId = decisions.createDecision("Should I continue?", sourceRevisionId = originalRevisionId)
                decisions.setChoice(decisionId, "Continue")
                decisions.recordOutcome(decisionId, "The result changed the context.")
                val latestRevision = reflection.revise(proposal.hypothesisId, "A newer current conclusion")
                val currentRevisionId = requireNotNull(latestRevision.revisionId)

                assertNull(decisions.getOutcomeImpact(decisionId))
                assertThrows(IllegalStateException::class.java) {
                    runBlocking {
                        decisions.applyOutcomeImpact(decisionId, "A stale proposal")
                    }
                }
                assertEquals(
                    currentRevisionId,
                    database.knowledgeDao().getAllConclusions().single().currentRevisionId
                )
                assertEquals(
                    "A newer current conclusion",
                    database.knowledgeDao().getRevisionById(currentRevisionId)?.text
                )
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
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository)
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
            val decisions = DecisionRepository(database, database.knowledgeDao(), reflection)
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
    fun decisionMappingQueryCountStaysBoundedForManyDecisions() {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ -> queries += sql },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val repository = DecisionRepository(
                database,
                database.knowledgeDao(),
                reflectionRepository(database)
            )
            runBlocking {
                val ids = 1L..10_000L
                database.knowledgeDao().insertRawRecords(
                    ids.map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "decision raw $id",
                            audioPath = null,
                            durationMs = 0L,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertConclusions(
                    ids.map { id ->
                        ConclusionEntity(
                            id = id,
                            rawRecordId = id,
                            currentRevisionId = id,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertRevisions(
                    ids.map { id ->
                        ConclusionRevisionEntity(
                            id = id,
                            conclusionId = id,
                            version = 1,
                            text = "decision grounds $id",
                            author = "user",
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertDecisions(
                    ids.map { id ->
                        DecisionEntity(
                            id = id,
                            question = "decision question $id",
                            choice = "chosen path",
                            sourceRevisionId = id,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertOutcomes(
                    ids.map { id ->
                        OutcomeEntity(
                            id = id + 2_000L,
                            decisionId = id,
                            report = "decision outcome $id",
                            createdAt = id
                        )
                    }
                )

                queries.clear()
                val decisions = repository.getDecisions()
                val selects = queries.selectStatements()

                assertEquals(10_000, decisions.size)
                assertTrue(decisions.all { it.outcomes.size == 1 })
                assertTrue(
                    "Decision mapping should use a bounded number of SELECTs, got ${selects.size}",
                    selects.size <= 4
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun decisionMappingDoesNotCombineChoiceAndOutcomeFromDifferentSnapshots() {
        val database = inMemoryDatabase()
        try {
            val reflection = reflectionRepository(database)
            val decisions = DecisionRepository(database, database.knowledgeDao(), reflection)
            runBlocking {
                val decisionId = decisions.createDecision(
                    question = "Which path should remain coherent?",
                    sourceRevisionId = currentRevision(database)
                )
                val snapshots = withContext(Dispatchers.Default) {
                    val writer = async {
                        decisions.setChoice(decisionId, "Path A")
                        repeat(40) { index ->
                            decisions.recordOutcome(decisionId, "Path A remained valid: $index")
                        }
                    }
                    val readers = (1..4).map {
                        async {
                            buildList {
                                repeat(100) { add(decisions.getDecisions().single()) }
                            }
                        }
                    }
                    val readerResults = readers.awaitAll()
                    writer.await()
                    readerResults.flatten()
                }

                assertTrue(
                    "A decision with an outcome must always expose its choice in one read snapshot",
                    snapshots.all { it.outcomes.isEmpty() || !it.choice.isNullOrBlank() }
                )
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
            val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository)
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

    private fun fileDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DATABASE = "decision-repository-test.db"
    }

    private fun List<String>.selectStatements(): List<String> =
        synchronized(this) {
            filter { statement ->
                statement.trimStart().startsWith("SELECT", ignoreCase = true) &&
                    !statement.lowercase().contains("room_table_modification_log")
            }
        }
}
