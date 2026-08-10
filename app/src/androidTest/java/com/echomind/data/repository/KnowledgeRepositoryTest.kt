package com.echomind.data.repository

import android.content.Context
import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.settings.SettingsStore
import com.echomind.ui.detail.DetailViewModel
import com.echomind.domain.model.KnowledgeSearchResult
import com.echomind.domain.model.CoverageScopeType
import com.echomind.domain.model.HomeCard
import com.echomind.domain.model.HomeCardType
import com.echomind.domain.model.Capability
import com.echomind.domain.model.Relationship
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class KnowledgeRepositoryTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun themesCanBeCreatedRenamedLinkedAndCounted() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("Career source one")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Career conclusion")
                val revisionId = requireNotNull(session.revisionId)

                val themeId = knowledgeRepository.createTheme("Career")
                knowledgeRepository.linkConclusionToTheme(themeId, revisionId)

                val theme = knowledgeRepository.getThemes().single()
                assertEquals("Career", theme.name)
                assertEquals(1, theme.conclusionCount)

                val conclusions = knowledgeRepository.getThemeConclusions(themeId)
                assertEquals(1, conclusions.size)
                assertEquals("Career conclusion", conclusions.single().conclusionText)

                knowledgeRepository.renameTheme(themeId, "Work")
                assertEquals("Work", knowledgeRepository.getThemeName(themeId))

                knowledgeRepository.unlinkConclusionFromTheme(themeId, revisionId)
                assertTrue(knowledgeRepository.getThemeConclusions(themeId).isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun relationshipsLinkAndUnlinkRecordsAsSupportsOrContradicts() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val firstRawId = reflectionRepository.captureRawText("I want to change roles.")
                val firstProposal = reflectionRepository.createLocalProposal(firstRawId)
                val firstSession = reflectionRepository.confirm(
                    firstProposal.hypothesisId,
                    "I should change roles."
                )

                val secondRawId = reflectionRepository.captureRawText(
                    "Changing roles added stress last time."
                )
                val secondProposal = reflectionRepository.createLocalProposal(secondRawId)
                val secondSession = reflectionRepository.confirm(
                    secondProposal.hypothesisId,
                    "Role changes added stress before."
                )

                val revisionId = requireNotNull(firstSession.revisionId)

                val candidatesBefore = knowledgeRepository.getLinkCandidates(revisionId)
                assertTrue(candidatesBefore.any { it.rawRecordId == secondRawId })
                assertTrue(candidatesBefore.all { it.suggestedReason != null })

                knowledgeRepository.linkRelatedRecord(
                    revisionId = revisionId,
                    sourceRecordId = secondRawId,
                    relationship = Relationship.CONTRADICTS
                )

                val related = knowledgeRepository.getRelatedRecords(revisionId)
                assertEquals(1, related.size)
                assertEquals(Relationship.CONTRADICTS, related.single().relationship)

                val candidates = knowledgeRepository.getLinkCandidates(revisionId)
                assertTrue(candidates.none { it.rawRecordId == secondRawId })

                knowledgeRepository.unlinkRelatedRecord(revisionId, secondRawId)
                assertTrue(knowledgeRepository.getRelatedRecords(revisionId).isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun archivingThemeHidesItButKeepsLinks() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("A confirmed source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "A conclusion")
                val revisionId = requireNotNull(session.revisionId)

                val themeId = knowledgeRepository.createTheme("Health")
                knowledgeRepository.linkConclusionToTheme(themeId, revisionId)
                knowledgeRepository.archiveTheme(themeId)

                assertTrue(knowledgeRepository.getThemes().isEmpty())
                assertEquals(1, knowledgeRepository.getThemeConclusions(themeId).size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun searchReturnsRawConclusionsAndThemesAcrossTheGraph() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("A career decision source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(
                    proposal.hypothesisId,
                    "The career decision is clear"
                )
                val themeId = knowledgeRepository.createTheme("Career planning")
                knowledgeRepository.linkConclusionToTheme(
                    themeId = themeId,
                    revisionId = requireNotNull(session.revisionId)
                )

                val results = knowledgeRepository.search("career")
                assertEquals(1, results.count { it is KnowledgeSearchResult.RawRecord })
                assertEquals(1, results.count { it is KnowledgeSearchResult.Conclusion })
                assertTrue(results.any { it is KnowledgeSearchResult.Theme })

                val onlyTheme = knowledgeRepository.search("planning")
                assertEquals(
                    1,
                    onlyTheme.count { it is KnowledgeSearchResult.Theme }
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun searchTreatsLikeWildcardsAsLiteralAndIncludesHistoricalRevisions() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val percentId = reflectionRepository.captureRawText("100% focus")
                val percentLookalikeId = reflectionRepository.captureRawText("1000 focus")
                val underscoreId = reflectionRepository.captureRawText("a_b marker")
                val underscoreLookalikeId = reflectionRepository.captureRawText("axb marker")

                val historicalRawId = reflectionRepository.captureRawText("History source")
                val proposal = reflectionRepository.createLocalProposal(historicalRawId)
                reflectionRepository.confirm(proposal.hypothesisId, "Old wording")
                reflectionRepository.revise(proposal.hypothesisId, "New wording")

                val percentResults = knowledgeRepository.search("100%")
                    .filterIsInstance<KnowledgeSearchResult.RawRecord>()
                    .map { it.rawRecordId }
                val underscoreResults = knowledgeRepository.search("a_b")
                    .filterIsInstance<KnowledgeSearchResult.RawRecord>()
                    .map { it.rawRecordId }
                val oldRevisionResults = knowledgeRepository.search("Old wording")
                    .filterIsInstance<KnowledgeSearchResult.Conclusion>()
                val newRevisionResults = knowledgeRepository.search("New wording")
                    .filterIsInstance<KnowledgeSearchResult.Conclusion>()

                assertEquals(listOf(percentId), percentResults)
                assertTrue(percentLookalikeId !in percentResults)
                assertEquals(listOf(underscoreId), underscoreResults)
                assertTrue(underscoreLookalikeId !in underscoreResults)
                assertEquals(1, oldRevisionResults.size)
                assertEquals(1, oldRevisionResults.single().revisionVersion)
                assertTrue(!oldRevisionResults.single().isCurrent)
                assertEquals(1, newRevisionResults.size)
                assertEquals(2, newRevisionResults.single().revisionVersion)
                assertTrue(newRevisionResults.single().isCurrent)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun searchQueryCountStaysBoundedForManyGraphMatches() {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ -> queries += sql },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val repository = knowledgeRepository(database)
            runBlocking {
                val ids = 1L..1_000L
                database.knowledgeDao().insertRawRecords(
                    ids.map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "needle raw $id",
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
                            text = "needle conclusion $id",
                            author = "user",
                            createdAt = id
                        )
                    }
                )

                queries.clear()
                val oneThousandResults = repository.search("needle")
                val oneThousandSelects = queries.selectStatements()

                database.knowledgeDao().insertRawRecords(
                    (1_001L..10_000L).map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "other raw $id",
                            audioPath = "/unneeded/audio-$id",
                            durationMs = 1L,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertConclusions(
                    (1_001L..10_000L).map { id ->
                        ConclusionEntity(
                            id = id,
                            rawRecordId = id,
                            currentRevisionId = id,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertRevisions(
                    (1_001L..10_000L).map { id ->
                        ConclusionRevisionEntity(
                            id = id,
                            conclusionId = id,
                            version = 1,
                            text = "other conclusion $id",
                            author = "user",
                            createdAt = id
                        )
                    }
                )

                queries.clear()
                val tenThousandResults = repository.search("needle")
                val tenThousandSelects = queries.selectStatements()

                assertEquals(1_000, oneThousandResults.count { it is KnowledgeSearchResult.RawRecord })
                assertEquals(1_000, oneThousandResults.count { it is KnowledgeSearchResult.Conclusion })
                assertEquals(1_000, tenThousandResults.count { it is KnowledgeSearchResult.RawRecord })
                assertEquals(1_000, tenThousandResults.count { it is KnowledgeSearchResult.Conclusion })
                assertTrue(
                    "Search SELECT count should stay bounded at 1k and 10k records: " +
                        "1k=${oneThousandSelects.size}, 10k=${tenThousandSelects.size}",
                    oneThousandSelects.size <= 6 && tenThousandSelects.size <= 6
                )
                assertTrue(
                    "Search should not fetch full raw-record entities",
                    tenThousandSelects.none { it.lowercase().contains("select * from raw_records") }
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun manualLinkCandidatesIncludeNoOverlapAndRecordsBeyondSuggestionLimit() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val currentRawId = reflectionRepository.captureRawText("Current reflection")
                val proposal = reflectionRepository.createLocalProposal(currentRawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Current conclusion")
                val currentRevisionId = requireNotNull(session.revisionId)
                repeat(6) { index ->
                    reflectionRepository.captureRawText("Unrelated archive record $index")
                }

                assertTrue(knowledgeRepository.getLinkCandidates(currentRevisionId).isEmpty())
                val manual = knowledgeRepository.getManualLinkCandidates(currentRevisionId)

                assertEquals(6, manual.size)
                assertTrue(manual.none { it.rawRecordId == currentRawId })
                assertTrue(manual.all { it.suggestedReason == null })
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun manualLinkSearchMatchesCyrillicRegardlessOfCase() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val currentRawId = reflectionRepository.captureRawText("Текущая запись")
                val proposal = reflectionRepository.createLocalProposal(currentRawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Текущий вывод")
                val currentRevisionId = requireNotNull(session.revisionId)
                val candidateId = reflectionRepository.captureRawText("КАРЬЕРА и следующий шаг")

                val matches = knowledgeRepository.getManualLinkCandidates(
                    currentRevisionId = currentRevisionId,
                    query = "карьера"
                )

                assertEquals(listOf(candidateId), matches.map { it.rawRecordId })
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun detailManualCandidateLoadUsesBoundedProjectionWithManyLinkedRecords() {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ -> queries += sql },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val currentRawId = reflectionRepository.captureRawText("Current reflection")
                val proposal = reflectionRepository.createLocalProposal(currentRawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Current conclusion")
                val currentRevisionId = requireNotNull(session.revisionId)
                val linkedRawIds = 2L..1_002L
                database.knowledgeDao().insertRawRecords(
                    linkedRawIds.map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "Linked archive record $id",
                            audioPath = "/unneeded/audio-$id",
                            durationMs = 0L,
                            createdAt = id
                        )
                    } + (2_000L..2_002L).map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "Unlinked archive record $id",
                            audioPath = null,
                            durationMs = 0L,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertEvidenceLinks(
                    linkedRawIds.map { rawId ->
                        EvidenceLinkEntity(
                            conclusionRevisionId = currentRevisionId,
                            sourceRawRecordId = rawId,
                            relationship = Relationship.SUPPORTS,
                            status = "confirmed"
                        )
                    }
                )

                queries.clear()
                val manual = knowledgeRepository.getManualLinkCandidates(currentRevisionId)

                assertEquals(3, manual.size)
                assertTrue(manual.all { it.sourceText.startsWith("Unlinked") })
                assertTrue(manual.none { it.rawRecordId in linkedRawIds })
                val selects = queries.selectStatements()
                assertTrue(
                    "Manual candidate flow must not fetch full raw-record entities",
                    selects.none { it.lowercase().contains("select * from raw_records") }
                )
                assertTrue(
                    "Manual candidate exclusion must not use a caller-sized NOT IN list",
                    selects.any { it.lowercase().contains("not exists") }
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun detailViewModelLoadsManualCandidatesAsPagesWithManyLinkedRecords() {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ -> queries += sql },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val reflectionRepository = reflectionRepository(database)
            runBlocking {
                val currentRawId = reflectionRepository.captureRawText("Current reflection")
                val proposal = reflectionRepository.createLocalProposal(currentRawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Current conclusion")
                val currentRevisionId = requireNotNull(session.revisionId)
                val linkedRawIds = 2L..1_002L
                database.knowledgeDao().insertRawRecords(
                    linkedRawIds.map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "Linked archive record $id",
                            audioPath = null,
                            durationMs = 0L,
                            createdAt = id
                        )
                    } + (2_000L..2_120L).map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "Unlinked archive record $id",
                            audioPath = null,
                            durationMs = 0L,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertEvidenceLinks(
                    linkedRawIds.map { rawId ->
                        EvidenceLinkEntity(
                            conclusionRevisionId = currentRevisionId,
                            sourceRawRecordId = rawId,
                            relationship = Relationship.SUPPORTS,
                            status = "confirmed"
                        )
                    }
                )

                val viewModel = DetailViewModel(
                    application = context.applicationContext as Application,
                    entryRepository = EntryRepository(
                        database,
                        database.entryDao(),
                        database.knowledgeDao(),
                        context
                    ),
                    reflectionRepository = reflectionRepository,
                    knowledgeRepository = knowledgeRepository(database),
                    audioEncryptionUtil = AudioEncryptionUtil(context)
                )
                queries.clear()
                viewModel.loadEntry(currentRawId)
                val loaded = withTimeout(30_000) {
                    viewModel.uiState.first { !it.isLoading }
                }

                assertTrue("Detail flow failed: ${loaded.error}", loaded.error == null)
                assertEquals(100, loaded.manualCandidates.size)
                assertTrue(loaded.manualCandidatesHasMore)
                assertTrue(loaded.otherEntries.size <= 5)
                assertTrue(queries.any { it.lowercase().contains("not exists") })

                viewModel.loadMoreManualCandidates()
                val secondPage = withTimeout(30_000) {
                    viewModel.uiState.first {
                        !it.manualCandidatesHasMore && it.manualCandidates.size == 121
                    }
                }
                assertEquals(121, secondPage.manualCandidates.size)

                viewModel.searchManualCandidates("Unlinked archive record 2120")
                val searched = withTimeout(30_000) {
                    viewModel.uiState.first {
                        it.manualQuery == "Unlinked archive record 2120" &&
                            it.manualCandidates.map { candidate -> candidate.rawRecordId } == listOf(2_120L)
                    }
                }
                assertEquals(listOf(2_120L), searched.manualCandidates.map { it.rawRecordId })
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun detailManualSearchCannotBeOvertakenByLoadMore() {
        val searchStarted = CountDownLatch(1)
        val releaseSearch = CountDownLatch(1)
        val blockNextSelect = AtomicBoolean(false)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ ->
                    if (sql.trimStart().startsWith("SELECT", ignoreCase = true) &&
                        blockNextSelect.compareAndSet(true, false)
                    ) {
                        searchStarted.countDown()
                        check(releaseSearch.await(30, TimeUnit.SECONDS)) {
                            "Timed out while holding the manual search query"
                        }
                    }
                },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val reflectionRepository = reflectionRepository(database)
            runBlocking {
                val currentRawId = reflectionRepository.captureRawText("Current reflection")
                val proposal = reflectionRepository.createLocalProposal(currentRawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Current conclusion")
                val currentRevisionId = requireNotNull(session.revisionId)
                reflectionRepository.captureRawText("Needle archive record")
                reflectionRepository.captureRawText("Other archive record")

                val viewModel = DetailViewModel(
                    application = context.applicationContext as Application,
                    entryRepository = EntryRepository(
                        database,
                        database.entryDao(),
                        database.knowledgeDao(),
                        context
                    ),
                    reflectionRepository = reflectionRepository,
                    knowledgeRepository = knowledgeRepository(database),
                    audioEncryptionUtil = AudioEncryptionUtil(context)
                )
                viewModel.loadEntry(currentRawId)
                withTimeout(30_000) { viewModel.uiState.first { !it.isLoading } }

                blockNextSelect.set(true)
                viewModel.searchManualCandidates("Needle")
                assertTrue(
                    "Search query must be visible before its result returns",
                    viewModel.uiState.value.manualQuery == "Needle"
                )
                assertTrue(searchStarted.await(30, TimeUnit.SECONDS))

                viewModel.loadMoreManualCandidates()
                releaseSearch.countDown()

                val settled = withTimeout(30_000) {
                    viewModel.uiState.first {
                        it.manualQuery == "Needle" &&
                            it.manualCandidates.map { candidate -> candidate.sourceText } ==
                            listOf("Needle archive record")
                    }
                }
                assertEquals(listOf("Needle archive record"), settled.manualCandidates.map { it.sourceText })
            }
        } finally {
            releaseSearch.countDown()
            database.close()
        }
    }

    @Test
    fun semanticLinksRejectDuplicatePairsAtDatabaseBoundary() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("Current source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "Current conclusion")
                val revisionId = requireNotNull(session.revisionId)
                val externalRawId = reflectionRepository.captureRawText("External source")
                val themeId = knowledgeRepository.createTheme("Work")

                database.knowledgeDao().insertThemeLink(
                    ThemeLinkEntity(
                        themeId = themeId,
                        conclusionRevisionId = revisionId,
                        confirmed = true,
                        createdAt = 1L
                    )
                )
                val duplicateThemeResult = database.knowledgeDao().insertThemeLink(
                    ThemeLinkEntity(
                        themeId = themeId,
                        conclusionRevisionId = revisionId,
                        confirmed = true,
                        createdAt = 2L
                    )
                )

                database.knowledgeDao().insertEvidenceLink(
                    EvidenceLinkEntity(
                        conclusionRevisionId = revisionId,
                        sourceRawRecordId = externalRawId,
                        relationship = "supports",
                        status = "confirmed"
                    )
                )
                val duplicateEvidenceResult = database.knowledgeDao().insertEvidenceLink(
                    EvidenceLinkEntity(
                        conclusionRevisionId = revisionId,
                        sourceRawRecordId = externalRawId,
                        relationship = "contradicts",
                        status = "confirmed"
                    )
                )

                assertEquals(-1L, duplicateThemeResult)
                assertEquals(-1L, duplicateEvidenceResult)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun addToThemeSurfacesAnExistingPendingLinkInsteadOfSilentlyIgnoringIt() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("Current source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val revisionId = requireNotNull(
                    reflectionRepository.confirm(proposal.hypothesisId, "Current conclusion").revisionId
                )
                val themeId = knowledgeRepository.createTheme("Work")
                database.knowledgeDao().insertThemeLink(
                    ThemeLinkEntity(
                        themeId = themeId,
                        conclusionRevisionId = revisionId,
                        confirmed = false,
                        createdAt = 1L,
                        origin = "model_suggested",
                        reviewRequired = true
                    )
                )

                assertThrows(IllegalStateException::class.java) {
                    runBlocking { knowledgeRepository.linkConclusionToTheme(themeId, revisionId) }
                }
                assertEquals(1, database.knowledgeDao().getPendingThemeLinksForRevision(revisionId).size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun homeCoverageKeepsTypedStatesAndDispositionUsesExactFingerprint() {
        val database = inMemoryDatabase()
        try {
            val reflectionRepository = reflectionRepository(database)
            val knowledgeRepository = knowledgeRepository(database)
            runBlocking {
                val rawId = reflectionRepository.captureRawText("A current source")
                val proposal = reflectionRepository.createLocalProposal(rawId)
                val session = reflectionRepository.confirm(proposal.hypothesisId, "A current conclusion")
                val revisionId = requireNotNull(session.revisionId)
                val themeId = knowledgeRepository.createTheme("Work")
                knowledgeRepository.linkConclusionToTheme(themeId, revisionId)

                val relevance = knowledgeRepository.getHomeRelevance()
                assertTrue(relevance.hasKnowledge)
                val coverage = relevance.coverage.single { it.scopeType == CoverageScopeType.THEME }
                assertEquals("Work", coverage.name)
                assertEquals(1, coverage.currentRevisionIds.size)
                assertEquals(com.echomind.domain.model.EvidenceState.NO_EXTERNAL_EVIDENCE, coverage.evidenceState)
                assertTrue(relevance.card == null)

                val card = HomeCard(
                    type = HomeCardType.THIN_EVIDENCE,
                    themeId = themeId,
                    themeName = "Work",
                    title = "Thin",
                    detail = "D",
                    reason = "R",
                    capability = Capability.REFLECTION,
                    cardKey = "fingerprint-v1",
                    scopeType = CoverageScopeType.THEME,
                    scopeId = themeId,
                    currentRevisionIds = listOf(revisionId)
                )
                knowledgeRepository.dismissCard(card)
                assertEquals("fingerprint-v1", knowledgeRepository.getCardDispositions().single().cardKey)
                knowledgeRepository.restoreCard("fingerprint-v1")
                assertTrue(knowledgeRepository.getCardDispositions().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun homeRelevanceQueryCountAndPayloadStayBoundedAtOneAndTenThousandRecords() {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ -> queries += sql },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val repository = knowledgeRepository(database)
            runBlocking {
                database.knowledgeDao().insertRawRecords(rawRecords(1L..1_000L))
                queries.clear()
                repository.getHomeRelevance()
                val oneThousandSelects = queries.selectStatements()

                database.knowledgeDao().insertRawRecords(rawRecords(1_001L..10_000L))
                queries.clear()
                repository.getHomeRelevance()
                val tenThousandSelects = queries.selectStatements()

                assertTrue(
                    "Home SELECT count should stay bounded at 1k and 10k records: " +
                        "1k=${oneThousandSelects.size}, 10k=${tenThousandSelects.size}",
                    oneThousandSelects.size <= 12 && tenThousandSelects.size <= 12
                )
                listOf(
                    "from raw_records order by",
                    "from conclusion_revisions order by",
                    "from evidence_links order by",
                    "from ai_hypotheses order by",
                    "from decisions order by",
                    "from outcomes order by"
                ).forEach { unboundedScan ->
                    assertTrue(
                        "Home should not execute an unbounded scan containing '$unboundedScan'",
                        tenThousandSelects.none { it.lowercase().contains(unboundedScan) }
                    )
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun linkCandidateQueriesStayBoundedAndUseOnlyRankingPayloadAtOneAndTenThousandRecords() {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ -> queries += sql },
                Executor { command -> command.run() }
            )
            .build()
        try {
            val repository = knowledgeRepository(database)
            runBlocking {
                database.knowledgeDao().insertRawRecord(
                    RawRecordEntity(
                        id = 1L,
                        originalText = "Current conclusion shared-term",
                        audioPath = null,
                        durationMs = 0L,
                        createdAt = 1L
                    )
                )
                database.knowledgeDao().insertConclusion(
                    ConclusionEntity(
                        id = 1L,
                        rawRecordId = 1L,
                        currentRevisionId = 1L,
                        createdAt = 2L
                    )
                )
                database.knowledgeDao().insertRevision(
                    ConclusionRevisionEntity(
                        id = 1L,
                        conclusionId = 1L,
                        version = 1,
                        text = "Current conclusion shared-term",
                        author = "user",
                        createdAt = 3L
                    )
                )
                database.knowledgeDao().insertRawRecords(
                    (2L..1_000L).map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "Candidate shared-term $id",
                            audioPath = "/unneeded/audio-$id",
                            durationMs = 1L,
                            createdAt = id
                        )
                    }
                )
                database.knowledgeDao().insertThemes(
                    (1L..100L).map { id ->
                        ThemeEntity(id = id, name = "Theme $id", createdAt = id)
                    }
                )
                database.knowledgeDao().insertThemeLinks(
                    (1L..100L).map { id ->
                        ThemeLinkEntity(
                            id = id,
                            themeId = id,
                            conclusionRevisionId = 1L,
                            confirmed = true,
                            createdAt = id
                        )
                    }
                )

                queries.clear()
                val oneThousand = repository.getLinkCandidates(1L)
                val oneThousandSelects = queries.selectStatements()

                database.knowledgeDao().insertRawRecords(
                    (1_001L..10_000L).map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "Candidate shared-term $id",
                            audioPath = "/unneeded/audio-$id",
                            durationMs = 1L,
                            createdAt = id
                        )
                    }
                )
                queries.clear()
                val tenThousand = repository.getLinkCandidates(1L)
                val tenThousandSelects = queries.selectStatements()

                assertTrue(oneThousand.size <= 5)
                assertTrue(tenThousand.size <= 5)
                assertTrue(
                    "Candidate query count should stay bounded at 1k and 10k records: " +
                        "1k=${oneThousandSelects.size}, 10k=${tenThousandSelects.size}",
                    oneThousandSelects.size <= 6 && tenThousandSelects.size <= 6
                )
                assertTrue(
                    "Candidate loading should use bounded queries, got ${tenThousandSelects.size}",
                    tenThousandSelects.size <= 6
                )
                assertTrue(
                    "Candidate loading must not fetch full raw-record entities",
                    tenThousandSelects.none {
                        it.lowercase().contains("select * from raw_records")
                    }
                )
            }
        } finally {
            database.close()
        }
    }

    private fun rawRecords(ids: LongRange): List<RawRecordEntity> = ids.map { id ->
        RawRecordEntity(
            id = id,
            originalText = "Unrelated record $id",
            audioPath = null,
            durationMs = 0L,
            createdAt = id
        )
    }

    private fun List<String>.selectStatements(): List<String> =
        synchronized(this) {
            filter { it.trimStart().startsWith("SELECT", ignoreCase = true) }
        }

    private fun knowledgeRepository(database: AppDatabase) =
        KnowledgeRepository(database, database.knowledgeDao(), SettingsStore(context))

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
        const val TEST_DATABASE = "knowledge-repository-test.db"
    }
}
