package com.echomind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.domain.model.Relationship
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
                knowledgeRepository.linkRelatedRecord(
                    revisionId = revisionId,
                    sourceRecordId = secondRawId,
                    relationship = Relationship.CONTRADICTS
                )

                val related = knowledgeRepository.getRelatedRecords(revisionId)
                assertEquals(1, related.size)
                assertEquals(Relationship.CONTRADICTS, related.single().relationship)

                val candidates = knowledgeRepository.getLinkCandidates(firstRawId)
                assertEquals(1, candidates.size)
                assertEquals(secondRawId, candidates.single().rawRecordId)

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

    private fun knowledgeRepository(database: AppDatabase) =
        KnowledgeRepository(database, database.knowledgeDao())

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
