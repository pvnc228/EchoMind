package com.echomind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.entity.AudioCleanupEntity
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.Relationship
import com.echomind.domain.model.ReflectionStatus
import com.echomind.domain.model.EntryDeletionChoice
import com.echomind.data.repository.DeletionDependenciesRequireExplicitChoiceException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReflectionRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun rawTextIsPersistedBeforeProposalGeneration() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText("My original words")

                assertEquals("My original words", database.knowledgeDao()
                    .getRawRecordById(rawRecordId)?.originalText)
                assertEquals(1, database.entryDao().getAllEntriesOnce().size)
                assertTrue(database.knowledgeDao().getAllHypotheses().isEmpty())
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())

                repository.createLocalProposal(rawRecordId)
                assertEquals(1, database.knowledgeDao().getAllHypotheses().size)
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectionIsDurableAndLeavesNoConclusion() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText("I think this is the only option.")
                val proposal = repository.createLocalProposal(rawRecordId)
                val rejected = repository.reject(proposal.hypothesisId)

                assertEquals(ReflectionStatus.REJECTED, rejected.status)
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())
                assertTrue(database.knowledgeDao().getAllRevisions().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun focusedFollowUpCreatesOneDurableProposalLinkedToTheOriginalSource() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText(
                    "Я думаю, что один короткий ответ всё доказывает."
                )
                val initial = repository.createLocalProposal(rawRecordId)

                val followUp = repository.continueDiscussion(
                    hypothesisId = initial.hypothesisId,
                    question = "Какие наблюдения могли бы изменить этот вывод?"
                )

                assertEquals(rawRecordId, followUp.rawRecordId)
                assertEquals(initial.hypothesisId, followUp.parentHypothesisId)
                assertEquals(
                    "Какие наблюдения могли бы изменить этот вывод?",
                    followUp.followUpQuestion
                )
                assertEquals(ReflectionStatus.PROPOSED, followUp.status)
                assertTrue(followUp.confirmedConclusion == null)
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())
                assertEquals(
                    followUp.followUpQuestion,
                    repository.loadReflection(followUp.hypothesisId).followUpQuestion
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun focusedFollowUpRejectsExtraStepsAndStaleParentActions() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText("I think this needs more evidence.")
                val initial = repository.createLocalProposal(rawRecordId)
                val followUp = repository.continueDiscussion(initial.hypothesisId, "What would change it?")

                assertThrows(IllegalStateException::class.java) {
                    runBlocking {
                        repository.continueDiscussion(followUp.hypothesisId, "One more question?")
                    }
                }
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { repository.continueDiscussion(initial.hypothesisId, "A second question?") }
                }
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { repository.confirm(initial.hypothesisId, "Stale confirmation") }
                }
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repository.continueDiscussion(999_999L, "Unknown parent") }
                }
                assertEquals(2, database.knowledgeDao().getAllHypotheses().size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun concurrentContinueRequestsCreateAtMostOneFollowUp() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val rawRecordId = repository.captureRawText("I think the evidence is incomplete.")
                val initial = repository.createLocalProposal(rawRecordId)

                val results = coroutineScope {
                    listOf(
                        async(Dispatchers.Default) {
                            runCatching {
                                repository.continueDiscussion(initial.hypothesisId, "Question A?")
                            }
                        },
                        async(Dispatchers.Default) {
                            runCatching {
                                repository.continueDiscussion(initial.hypothesisId, "Question B?")
                            }
                        }
                    ).awaitAll()
                }

                assertEquals(1, results.count { it.isSuccess })
                assertEquals(1, results.count { it.isFailure })
                assertEquals(2, database.knowledgeDao().getAllHypotheses().size)
                assertNotNull(database.knowledgeDao().getFollowUpHypothesis(initial.hypothesisId))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun followUpUsesInjectedDispatcherAndRevalidatesStaleParentBeforeInsert() {
        val database = inMemoryDatabase()
        val analysisStarted = CountDownLatch(1)
        val releaseAnalysis = CountDownLatch(1)
        val analysisDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                Dispatchers.Default.dispatch(context) {
                    analysisStarted.countDown()
                    releaseAnalysis.await(5, TimeUnit.SECONDS)
                    block.run()
                }
            }
        }

        try {
            val repository = repository(database, analysisDispatcher)
            runBlocking {
                val rawRecordId = repository.captureRawText("Source must be rechecked.")
                val initial = repository.createLocalProposal(rawRecordId)
                val followUpRequest = async(Dispatchers.Default) {
                    runCatching {
                        repository.continueDiscussion(initial.hypothesisId, "What changed?")
                    }
                }

                assertTrue("analysis must use the injected dispatcher", analysisStarted.await(5, TimeUnit.SECONDS))
                val rejected = repository.reject(initial.hypothesisId)
                assertEquals(ReflectionStatus.REJECTED, rejected.status)
                releaseAnalysis.countDown()

                assertTrue(followUpRequest.await().isFailure)
                assertEquals(1, database.knowledgeDao().getAllHypotheses().size)
                assertEquals(rawRecordId, database.knowledgeDao().getAllRawRecords().single().id)
            }
        } finally {
            releaseAnalysis.countDown()
            database.close()
        }
    }

    @Test
    fun deletingTheRawSourceRemovesItsBoundedFollowUpGraph() {
        val database = inMemoryDatabase()

        try {
            val reflectionRepository = repository(database)
            val entryRepository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao(),
                context
            )
            runBlocking {
                val rawRecordId = reflectionRepository.captureRawText("Delete this source")
                val initial = reflectionRepository.createLocalProposal(rawRecordId)
                reflectionRepository.continueDiscussion(initial.hypothesisId, "What next?")
                val entryId = requireNotNull(
                    database.knowledgeDao().getRawRecordById(rawRecordId)?.legacyEntryId
                )

                entryRepository.deleteEntry(entryId)

                assertTrue(database.knowledgeDao().getAllRawRecords().isEmpty())
                assertTrue(database.knowledgeDao().getAllHypotheses().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun focusedFollowUpCanBeEditedAndAcceptedOrRejectedExplicitly() {
        val database = inMemoryDatabase()

        try {
            val repository = repository(database)
            runBlocking {
                val acceptedRawId = repository.captureRawText("Accepted follow-up source")
                val acceptedInitial = repository.createLocalProposal(acceptedRawId)
                val acceptedFollowUp = repository.continueDiscussion(
                    acceptedInitial.hypothesisId,
                    "What evidence matters?"
                )
                val confirmed = repository.confirm(
                    acceptedFollowUp.hypothesisId,
                    "My edited follow-up conclusion"
                )
                assertEquals(ReflectionStatus.CONFIRMED, confirmed.status)
                assertEquals("My edited follow-up conclusion", confirmed.confirmedConclusion)
                assertEquals(acceptedRawId, database.knowledgeDao().getAllEvidenceLinks().single().sourceRawRecordId)
                assertEquals("user", database.knowledgeDao().getAllRevisions().single().author)

                val rejectedRawId = repository.captureRawText("Rejected follow-up source")
                val rejectedInitial = repository.createLocalProposal(rejectedRawId)
                val rejectedFollowUp = repository.continueDiscussion(
                    rejectedInitial.hypothesisId,
                    "Should I keep this proposal?"
                )
                val rejected = repository.reject(rejectedFollowUp.hypothesisId)
                assertEquals(ReflectionStatus.REJECTED, rejected.status)
                assertEquals(1, database.knowledgeDao().getAllConclusions().size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun editedConfirmationCreatesRevisionOneLinkedToSourceAndSurvivesReopen() {
        val firstDatabase = fileDatabase()
        var hypothesisId = 0L
        var rawRecordId = 0L

        try {
            val repository = repository(firstDatabase)
            runBlocking {
                rawRecordId = repository.captureRawText(
                    "I think one rejected draft proves I cannot be an architect."
                )
                val proposal = repository.createLocalProposal(rawRecordId)
                hypothesisId = proposal.hypothesisId
                repository.confirm(
                    hypothesisId,
                    "One rejected draft is evidence about this attempt, not a verdict on my ability."
                )
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val restored = runBlocking {
                repository(reopenedDatabase).loadReflection(hypothesisId)
            }

            assertEquals(ReflectionStatus.CONFIRMED, restored.status)
            assertEquals(rawRecordId, restored.rawRecordId)
            assertEquals(1, restored.revisionVersion)
            assertEquals(
                "One rejected draft is evidence about this attempt, not a verdict on my ability.",
                restored.confirmedConclusion
            )
            val conclusion = runBlocking {
                reopenedDatabase.knowledgeDao().getAllConclusions().single()
            }
            assertNotNull(conclusion.currentRevisionId)
            val link = runBlocking {
                reopenedDatabase.knowledgeDao().getAllEvidenceLinks().single()
            }
            assertEquals(rawRecordId, link.sourceRawRecordId)
            assertEquals(ReflectionStatus.CONFIRMED, link.status)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun proposedReflectionCanBeRestoredAfterReopen() {
        val firstDatabase = fileDatabase()
        try {
            runBlocking {
                val repository = repository(firstDatabase)
                val rawRecordId = repository.captureRawText("I think I need more evidence.")
                repository.createLocalProposal(rawRecordId)
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val restored = runBlocking {
                repository(reopenedDatabase).loadLatestProposedReflection()
            }
            assertNotNull(restored)
            assertEquals(ReflectionStatus.PROPOSED, restored?.status)
            assertEquals("I think I need more evidence.", restored?.originalText)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun focusedFollowUpProposalCanBeRestoredAfterReopenWithoutChangingTheRawSource() {
        val firstDatabase = fileDatabase()
        var rawRecordId = 0L
        try {
            runBlocking {
                val repository = repository(firstDatabase)
                rawRecordId = repository.captureRawText("Original source remains immutable.")
                val initial = repository.createLocalProposal(rawRecordId)
                repository.continueDiscussion(initial.hypothesisId, "What should I check next?")
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val restored = runBlocking {
                repository(reopenedDatabase).loadLatestProposedReflection()
            }
            assertNotNull(restored)
            assertEquals(rawRecordId, restored?.rawRecordId)
            assertEquals("Original source remains immutable.", restored?.originalText)
            assertEquals("What should I check next?", restored?.followUpQuestion)
            assertEquals(
                1,
                runBlocking { reopenedDatabase.knowledgeDao().getAllRawRecords().size }
            )
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun reviseCreatesNewRevisionKeepsHistoricalLinksAndDoesNotRebaseThem() {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            val knowledgeRepository = KnowledgeRepository(database, database.knowledgeDao(), SettingsStore(context))
            runBlocking {
                val rawId = repository.captureRawText("I am torn about changing roles.")
                val proposal = repository.createLocalProposal(rawId)
                val confirmed = repository.confirm(proposal.hypothesisId, "I should change roles.")
                val revisionId = requireNotNull(confirmed.revisionId)

                val themeId = knowledgeRepository.createTheme("Career")
                knowledgeRepository.linkConclusionToTheme(themeId, revisionId)
                val secondRawId = repository.captureRawText("Past role changes added stress.")
                val secondProposal = repository.createLocalProposal(secondRawId)
                repository.confirm(secondProposal.hypothesisId, "Role changes add stress.")
                knowledgeRepository.linkRelatedRecord(
                    revisionId = revisionId,
                    sourceRecordId = secondRawId,
                    relationship = Relationship.CONTRADICTS
                )

                val revised = repository.revise(proposal.hypothesisId, "I should change roles carefully.")
                assertEquals(2, revised.revisionVersion)
                assertEquals("I should change roles carefully.", revised.confirmedConclusion)

                val conclusion = database.knowledgeDao().getConclusionForRawRecord(rawId)!!
                val revisions = database.knowledgeDao().getRevisionsForConclusion(conclusion.id)
                assertEquals(2, revisions.size)
                assertEquals(1, revisions[0].version)
                assertEquals("I should change roles.", revisions[0].text)
                assertEquals(2, revisions[1].version)

                val newRevisionId = requireNotNull(revised.revisionId)
                assertEquals(newRevisionId, conclusion.currentRevisionId)
                assertTrue(
                    database.knowledgeDao().getConfirmedThemeLink(themeId, revisionId) != null
                )
                assertTrue(
                    database.knowledgeDao().getConfirmedThemeLink(themeId, newRevisionId) == null
                )
                assertEquals(
                    2,
                    database.knowledgeDao().getEvidenceLinksForRevision(revisionId).size
                )
                assertEquals(
                    2,
                    database.knowledgeDao().getEvidenceLinksForRevision(newRevisionId).size
                )
                assertEquals(1, knowledgeRepository.getRelatedRecords(revisionId).size)
                assertTrue(knowledgeRepository.getRelatedRecords(newRevisionId).isEmpty())
                assertEquals(1, knowledgeRepository.getPendingRelatedRecords(newRevisionId).size)
                assertEquals(1, knowledgeRepository.getPendingThemesForRevision(newRevisionId).size)

                val pendingEvidence = knowledgeRepository.getPendingRelatedRecords(newRevisionId).single()
                knowledgeRepository.reviewPendingRelatedRecord(pendingEvidence.linkId, accept = true)
                val pendingTheme = knowledgeRepository.getPendingThemesForRevision(newRevisionId).single()
                knowledgeRepository.reviewPendingThemeLink(pendingTheme.linkId, accept = false)

                assertEquals(1, knowledgeRepository.getRelatedRecords(newRevisionId).size)
                assertTrue(knowledgeRepository.getPendingThemesForRevision(newRevisionId).isEmpty())
                assertTrue(knowledgeRepository.getConclusionsForRevision(newRevisionId).isEmpty())
                assertEquals(1, knowledgeRepository.getConclusionsForRevision(revisionId).size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun confirmedReflectionRequiresExplicitGraphDeletionThenRemovesEverything() {
        val database = inMemoryDatabase()
        val audioFile = File(context.cacheDir, "confirmed-reflection-audio.enc").apply {
            writeText("encrypted placeholder")
        }

        try {
            val reflectionRepository = repository(database)
            val entryRepository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao(),
                context
            )
            runBlocking {
                val rawRecordId = reflectionRepository.captureRawText(
                    originalText = "A source that becomes a conclusion.",
                    audioPath = audioFile.absolutePath
                )
                val proposal = reflectionRepository.createLocalProposal(rawRecordId)
                reflectionRepository.confirm(proposal.hypothesisId, "Confirmed wording")
                val entryId = requireNotNull(
                    database.knowledgeDao().getRawRecordById(rawRecordId)?.legacyEntryId
                )

                var protected = false
                try {
                    entryRepository.deleteEntry(entryId)
                } catch (_: ConfirmedConclusionDeletionRequiredException) {
                    protected = true
                }
                assertTrue(protected)
                assertEquals(1, database.entryDao().getAllEntriesOnce().size)
                assertEquals(1, database.knowledgeDao().getAllConclusions().size)
                assertTrue(audioFile.exists())

                entryRepository.deleteEntry(
                    id = entryId,
                    includeConfirmedConclusion = true
                )
                assertTrue(database.entryDao().getAllEntriesOnce().isEmpty())
                assertTrue(database.knowledgeDao().getAllRawRecords().isEmpty())
                assertTrue(database.knowledgeDao().getAllHypotheses().isEmpty())
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())
                assertTrue(database.knowledgeDao().getAllRevisions().isEmpty())
                assertTrue(database.knowledgeDao().getAllEvidenceLinks().isEmpty())
                assertTrue(!audioFile.exists())
            }
        } finally {
            database.close()
            audioFile.delete()
        }
    }

    @Test
    fun captureDraftRoundTripsAndSubmitRemovesItAtomically() {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            runBlocking {
                repository.saveCaptureDraft(
                    text = "Draft text",
                    encryptedAudioPath = "/app/audio/draft.enc",
                    durationMs = 1200L
                )
                val restored = requireNotNull(repository.loadCaptureDraft())
                assertEquals("Draft text", restored.text)
                assertEquals("/app/audio/draft.enc", restored.encryptedAudioPath)
                assertEquals(1200L, restored.durationMs)

                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repository.submitCaptureDraft("") }
                }
                assertNotNull(repository.loadCaptureDraft())

                val rawId = repository.submitCaptureDraft(
                    originalText = restored.text,
                    audioPath = restored.encryptedAudioPath,
                    durationMs = restored.durationMs
                )
                assertTrue(rawId > 0L)
                assertTrue(repository.loadCaptureDraft() == null)
                assertEquals(1, database.knowledgeDao().getAllRawRecords().size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun blankDraftStateRemovesThePersistedDraft() {
        val database = inMemoryDatabase()
        try {
            val repository = repository(database)
            runBlocking {
                repository.saveCaptureDraft(text = "abc")
                assertNotNull(repository.loadCaptureDraft())

                repository.saveCaptureDraft(text = "")

                assertTrue(repository.loadCaptureDraft() == null)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun externalEvidenceDeletionRequiresPreviewAndExplicitUnlink() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = repository(database)
            val knowledgeRepository = KnowledgeRepository(database, database.knowledgeDao(), SettingsStore(context))
            val entryRepository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao(),
                context
            )
            runBlocking {
                val sourceRawId = reflectionRepository.captureRawText("External source")
                val conclusionRawId = reflectionRepository.captureRawText("Conclusion source")
                val proposal = reflectionRepository.createLocalProposal(conclusionRawId)
                reflectionRepository.confirm(proposal.hypothesisId, "A conclusion")
                val revisionId = requireNotNull(
                    database.knowledgeDao().getConclusionForRawRecord(conclusionRawId)?.currentRevisionId
                )
                knowledgeRepository.linkRelatedRecord(
                    revisionId = revisionId,
                    sourceRecordId = sourceRawId,
                    relationship = Relationship.SUPPORTS
                )
                val entryId = requireNotNull(
                    database.knowledgeDao().getRawRecordById(sourceRawId)?.legacyEntryId
                )
                val plan = requireNotNull(entryRepository.getDeletionPlan(entryId))
                assertEquals(1, plan.incomingEvidence.size)
                assertTrue(plan.ownConclusionId == null)

                assertThrows(DeletionDependenciesRequireExplicitChoiceException::class.java) {
                    runBlocking { entryRepository.deleteEntry(entryId) }
                }
                assertEquals(1, database.knowledgeDao().getIncomingEvidenceLinks(sourceRawId).size)

                entryRepository.deleteEntry(
                    entryId,
                    EntryDeletionChoice(
                        unlinkIncomingEvidenceLinkIds = plan.incomingEvidence.map { it.linkId }.toSet()
                    )
                )
                assertTrue(database.knowledgeDao().getRawRecordById(sourceRawId) == null)
                assertEquals(1, database.knowledgeDao().getAllConclusions().size)
                assertTrue(
                    database.knowledgeDao().getEvidenceLinksForRevision(revisionId)
                        .none { it.sourceRawRecordId == sourceRawId }
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun deletionChoiceRejectsForeignDecisionAndLinkWithoutChangingTheGraph() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = repository(database)
            val knowledgeRepository = KnowledgeRepository(database, database.knowledgeDao(), SettingsStore(context))
            val entryRepository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao(),
                context
            )
            runBlocking {
                val targetRawId = reflectionRepository.captureRawText("Target without graph dependencies")
                val conclusionRawId = reflectionRepository.captureRawText("Unrelated conclusion")
                val foreignSourceRawId = reflectionRepository.captureRawText("Unrelated evidence")
                val proposal = reflectionRepository.createLocalProposal(conclusionRawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Unrelated conclusion wording")
                val revisionId = requireNotNull(session.revisionId)
                knowledgeRepository.linkRelatedRecord(
                    revisionId = revisionId,
                    sourceRecordId = foreignSourceRawId,
                    relationship = Relationship.SUPPORTS
                )
                val decisionRepository = DecisionRepository(database, database.knowledgeDao(), reflectionRepository)
                val decisionId = decisionRepository.createDecision(
                    question = "Unrelated question",
                    sourceRevisionId = revisionId
                )
                val foreignLinkId = database.knowledgeDao()
                    .getEvidenceLinksForRevision(revisionId)
                    .first { it.sourceRawRecordId == foreignSourceRawId }
                    .id
                val targetEntryId = requireNotNull(
                    database.knowledgeDao().getRawRecordById(targetRawId)?.legacyEntryId
                )
                val beforeRaw = database.knowledgeDao().getAllRawRecords()
                val beforeDecisions = database.knowledgeDao().getAllDecisions()
                val beforeLinks = database.knowledgeDao().getAllEvidenceLinks()

                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        entryRepository.deleteEntry(
                            targetEntryId,
                            EntryDeletionChoice(
                                deleteDecisionIds = setOf(decisionId),
                                unlinkIncomingEvidenceLinkIds = setOf(foreignLinkId)
                            )
                        )
                    }
                }

                assertEquals(beforeRaw, database.knowledgeDao().getAllRawRecords())
                assertEquals(beforeDecisions, database.knowledgeDao().getAllDecisions())
                assertEquals(beforeLinks, database.knowledgeDao().getAllEvidenceLinks())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun failedAudioCleanupIsPersistedAndRetriedInBoundedBatches() {
        val database = inMemoryDatabase()
        val audioDirectory = File(
            context.cacheDir,
            "failed-audio-cleanup-${System.nanoTime()}"
        ).apply { mkdirs() }
        try {
            val reflectionRepository = repository(database)
            val entryRepository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao(),
                context
            )
            runBlocking {
                val rawRecordId = reflectionRepository.captureRawText(
                    originalText = "Audio cleanup failure",
                    audioPath = audioDirectory.absolutePath
                )
                val entryId = requireNotNull(
                    database.knowledgeDao().getRawRecordById(rawRecordId)?.legacyEntryId
                )

                assertThrows(AudioDeletionFailedException::class.java) {
                    runBlocking { entryRepository.deleteEntry(entryId) }
                }
                assertTrue(database.entryDao().getEntryById(entryId) == null)
                assertEquals(1, entryRepository.getPendingAudioCleanup().size)
                assertEquals(
                    audioDirectory.absolutePath,
                    entryRepository.getPendingAudioCleanup().single().path
                )
                assertEquals(0, entryRepository.retryPendingAudioCleanup())
                assertTrue(entryRepository.getPendingAudioCleanup().single().attemptCount >= 2)

                assertTrue(audioDirectory.delete())
                assertEquals(1, entryRepository.retryPendingAudioCleanup())
                assertTrue(entryRepository.getPendingAudioCleanup().isEmpty())
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking {
                        entryRepository.getPendingAudioCleanup(
                            EntryRepository.MAX_AUDIO_CLEANUP_BATCH + 1
                        )
                    }
                }
            }
        } finally {
            database.close()
            audioDirectory.delete()
        }
    }

    @Test
    fun audioCleanupSkipsTerminalRowsAndRetriesEligibleRowsBeyondTheBatchWindow() {
        val database = inMemoryDatabase()
        val terminalDirectories = (0 until EntryRepository.MAX_AUDIO_CLEANUP_BATCH).map { index ->
            File(
                context.cacheDir,
                "terminal-audio-cleanup-${System.nanoTime()}-$index"
            ).apply { mkdirs() }
        }
        val eligibleDirectory = File(
            context.cacheDir,
            "eligible-audio-cleanup-${System.nanoTime()}"
        ).apply { mkdirs() }

        try {
            val entryRepository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao(),
                context
            )
            runBlocking {
                terminalDirectories.forEachIndexed { index, directory ->
                    database.knowledgeDao().upsertAudioCleanup(
                        AudioCleanupEntity(
                            path = directory.absolutePath,
                            entryId = index.toLong() + 1,
                            failedAt = index.toLong(),
                            attemptCount = EntryRepository.MAX_AUDIO_CLEANUP_ATTEMPTS
                        )
                    )
                }
                database.knowledgeDao().upsertAudioCleanup(
                    AudioCleanupEntity(
                        path = eligibleDirectory.absolutePath,
                        entryId = 10_000L,
                        failedAt = terminalDirectories.size.toLong(),
                        attemptCount = EntryRepository.MAX_AUDIO_CLEANUP_ATTEMPTS - 1
                    )
                )

                assertEquals(
                    listOf(eligibleDirectory.absolutePath),
                    entryRepository.getPendingAudioCleanup().map { it.path }
                )
                assertEquals(0, entryRepository.retryPendingAudioCleanup())
                assertEquals(
                    EntryRepository.MAX_AUDIO_CLEANUP_ATTEMPTS,
                    database.knowledgeDao().getAudioCleanup(eligibleDirectory.absolutePath)?.attemptCount
                )
                terminalDirectories.forEach { directory ->
                    assertEquals(
                        EntryRepository.MAX_AUDIO_CLEANUP_ATTEMPTS,
                        database.knowledgeDao().getAudioCleanup(directory.absolutePath)?.attemptCount
                    )
                }
                assertTrue(entryRepository.getPendingAudioCleanup().isEmpty())
            }
        } finally {
            database.close()
            terminalDirectories.forEach(File::delete)
            eligibleDirectory.delete()
        }
    }

    @Test
    fun audioCleanupQueueSurvivesDatabaseReopen() {
        val audioDirectory = File(
            context.cacheDir,
            "restart-audio-cleanup-${System.nanoTime()}"
        ).apply { mkdirs() }
        val firstDatabase = fileDatabase()
        try {
            val firstRepository = repository(firstDatabase)
            val firstEntryRepository = EntryRepository(
                firstDatabase,
                firstDatabase.entryDao(),
                firstDatabase.knowledgeDao(),
                context
            )
            runBlocking {
                val rawRecordId = firstRepository.captureRawText(
                    originalText = "Cleanup survives restart",
                    audioPath = audioDirectory.absolutePath
                )
                val entryId = requireNotNull(
                    firstDatabase.knowledgeDao().getRawRecordById(rawRecordId)?.legacyEntryId
                )
                assertThrows(AudioDeletionFailedException::class.java) {
                    runBlocking { firstEntryRepository.deleteEntry(entryId) }
                }
                assertEquals(1, firstEntryRepository.getPendingAudioCleanupCount())
            }
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = fileDatabase()
        try {
            val reopenedEntryRepository = EntryRepository(
                reopenedDatabase,
                reopenedDatabase.entryDao(),
                reopenedDatabase.knowledgeDao(),
                context
            )
            runBlocking {
                assertEquals(1, reopenedEntryRepository.getPendingAudioCleanupCount())
                assertTrue(audioDirectory.delete())
                assertEquals(1, reopenedEntryRepository.retryPendingAudioCleanup())
                assertEquals(0, reopenedEntryRepository.getPendingAudioCleanupCount())
            }
        } finally {
            reopenedDatabase.close()
            audioDirectory.delete()
        }
    }

    private fun repository(
        database: AppDatabase,
        analysisDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) = ReflectionRepository(
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        analyzer = LocalReflectionAnalyzer(),
        json = Json { ignoreUnknownKeys = true },
        analysisDispatcher = analysisDispatcher
    )

    private fun inMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun fileDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .allowMainThreadQueries()
            .build()

    private companion object {
        const val TEST_DATABASE = "reflection-repository-test.db"
    }
}
