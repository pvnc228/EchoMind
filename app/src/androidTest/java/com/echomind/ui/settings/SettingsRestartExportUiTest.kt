package com.echomind.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.export.ExportManager
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.entity.HomeCardDispositionEntity
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.remote.BaseUrlProvider
import com.echomind.data.remote.CredentialsProvider
import com.echomind.data.repository.EntryRepository
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsRestartExportUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val databasesToClose = mutableListOf<AppDatabase>()
    private val databaseNamesToDelete = mutableListOf<String>()
    private val filesToDelete = mutableListOf<File>()

    @After
    fun cleanup() {
        databasesToClose.forEach(AppDatabase::close)
        databaseNamesToDelete.forEach(context::deleteDatabase)
        filesToDelete.forEach { it.delete() }
    }

    @Test
    fun settingsScreenManagesDismissedHomeCardAfterDatabaseReopen() {
        val databaseName = "settings-ui-restart-${System.nanoTime()}.db"
        val first = fileDatabase(databaseName)
        seedDisposition(first)
        first.close()
        databasesToClose.remove(first)

        val reopened = fileDatabase(databaseName)
        val viewModel = settingsViewModel(reopened)
        renderAndRestoreDismissedCard(viewModel)
    }

    @Test
    fun settingsScreenManagesDismissedHomeCardAfterZipRestore() {
        val source = inMemoryDatabase()
        val target = inMemoryDatabase()
        seedDisposition(source)
        val exportFile = runBlocking {
            val archive = exportManager(source).exportToZip().getOrThrow()
            exportManager(target).restoreFromZip(archive).getOrThrow()
            archive
        }
        filesToDelete += exportFile

        val viewModel = settingsViewModel(target)
        renderAndRestoreDismissedCard(viewModel)
    }

    @Test
    fun settingsShowsRestoreScopeBeforeWritingToTarget() {
        val source = inMemoryDatabase()
        val target = inMemoryDatabase()
        val sourceReflection = com.echomind.data.repository.ReflectionRepository(
            source,
            source.entryDao(),
            source.knowledgeDao(),
            com.echomind.data.analysis.LocalReflectionAnalyzer(),
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        )
        val archive = runBlocking {
            sourceReflection.captureRawText("First imported source")
            sourceReflection.captureRawText("Second imported source")
            exportManager(source).exportToZip().getOrThrow()
        }
        filesToDelete += archive

        val viewModel = settingsViewModel(target)
        composeTestRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        viewModel.restoreData(Uri.fromFile(archive))

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Review restore scope")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Review restore scope").assertIsDisplayed()
        composeTestRule.onNodeWithText("First imported source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second imported source").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("First imported source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restore selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Merge all").assertIsDisplayed()
        runBlocking {
            assertEquals(0, target.knowledgeDao().getAllRawRecords().size)
        }

        val initialPreview = viewModel.uiState.value.restoreState as RestoreState.PreviewReady
        viewModel.toggleRestoreRoot(initialPreview.preview.availableRoots.first().rawRecordId, selected = false)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Selected records: 1.", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Restore selected").performClick()
        composeTestRule.waitUntil(5_000) {
            runBlocking { target.knowledgeDao().getAllRawRecords().size == 1 }
        }
        runBlocking {
            assertEquals(
                listOf("Second imported source"),
                target.knowledgeDao().getAllRawRecords().map { it.originalText }
            )
        }
    }

    private fun renderAndRestoreDismissedCard(viewModel: SettingsViewModel) {
        composeTestRule.setContent {
            SettingsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Dismissed Home cards")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("Dismissed Home cards").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONTRADICTION · THEME 42").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restore").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("CONTRADICTION · THEME 42")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeTestRule.onAllNodesWithText("CONTRADICTION · THEME 42")
            .fetchSemanticsNodes()
            .let { nodes -> check(nodes.isEmpty()) { "Dismissed card remained visible after Restore" } }
    }

    private fun seedDisposition(database: AppDatabase) {
        runBlocking {
            database.knowledgeDao().upsertHomeCardDisposition(
                HomeCardDispositionEntity(
                    cardKey = "theme:42:contradiction:v1",
                    cardType = "CONTRADICTION",
                    scopeType = "THEME",
                    scopeId = 42L,
                    dismissedAt = 100L,
                    postponedUntil = null,
                    createdAt = 50L
                )
            )
        }
    }

    private fun settingsViewModel(database: AppDatabase): SettingsViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val knowledgeRepository = KnowledgeRepository(
            database,
            database.knowledgeDao(),
            SettingsStore(context)
        )
        val entryRepository = EntryRepository(
            database,
            database.entryDao(),
            database.knowledgeDao(),
            context
        )
        return SettingsViewModel(
            application = app,
            baseUrlProvider = BaseUrlProvider(),
            exportManager = exportManager(database),
            credentialsProvider = CredentialsProvider(context),
            settingsStore = SettingsStore(context),
            knowledgeRepository = knowledgeRepository,
            entryRepository = entryRepository,
            ioDispatcher = Dispatchers.IO
        )
    }

    private fun exportManager(database: AppDatabase) = ExportManager(
        context = context,
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        audioEncryptionUtil = AudioEncryptionUtil(context)
    )

    private fun fileDatabase(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .build()
            .also {
                databasesToClose += it
                databaseNamesToDelete += name
            }

    private fun inMemoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also(databasesToClose::add)
}
