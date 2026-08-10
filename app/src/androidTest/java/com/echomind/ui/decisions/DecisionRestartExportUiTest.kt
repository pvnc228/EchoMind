package com.echomind.ui.decisions

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.export.ExportManager
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.repository.DecisionRepository
import com.echomind.data.repository.ReflectionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class DecisionRestartExportUiTest {

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
    fun decisionsScreenRendersChoiceAndOutcomeAfterDatabaseReopen() {
        val databaseName = "decision-ui-restart-${System.nanoTime()}.db"
        val first = fileDatabase(databaseName)
        seedDecision(first, "Restarted decision", "Keep it", "Outcome survived restart")
        first.close()
        databasesToClose.remove(first)

        val reopened = fileDatabase(databaseName)
        val viewModel = DecisionsViewModel(
            DecisionRepository(reopened, reopened.knowledgeDao(), reflectionRepository(reopened))
        )
        renderAndAssertDecision(viewModel, "Restarted decision", "Keep it", "Outcome survived restart")
    }

    @Test
    fun decisionsScreenRendersChoiceAndOutcomeAfterZipRestore() {
        val source = inMemoryDatabase()
        val target = inMemoryDatabase()
        seedDecision(source, "Restored decision", "Restore it", "Outcome survived restore")
        val exportFile = runBlocking {
            val archive = exportManager(source).exportToZip().getOrThrow()
            exportManager(target).restoreFromZip(archive).getOrThrow()
            archive
        }
        filesToDelete += exportFile

        val viewModel = DecisionsViewModel(
            DecisionRepository(target, target.knowledgeDao(), reflectionRepository(target))
        )
        renderAndAssertDecision(viewModel, "Restored decision", "Restore it", "Outcome survived restore")
    }

    private fun renderAndAssertDecision(
        viewModel: DecisionsViewModel,
        question: String,
        choice: String,
        outcome: String
    ) {
        composeTestRule.setContent {
            DecisionsScreen(onNavigateBack = {}, viewModel = viewModel)
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(question).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(question).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your choice: $choice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outcome: $outcome").assertIsDisplayed()
    }

    private fun seedDecision(
        database: AppDatabase,
        question: String,
        choice: String,
        outcome: String
    ) {
        val reflection = reflectionRepository(database)
        runBlocking {
            val rawId = reflection.captureRawText("Decision source for $question")
            val proposal = reflection.createLocalProposal(rawId)
            val revisionId = requireNotNull(
                reflection.confirm(proposal.hypothesisId, "Grounds for $question").revisionId
            )
            val decisions = DecisionRepository(database, database.knowledgeDao(), reflection)
            val decisionId = decisions.createDecision(question, sourceRevisionId = revisionId)
            decisions.setChoice(decisionId, choice)
            decisions.recordOutcome(decisionId, outcome)
        }
    }

    private fun exportManager(database: AppDatabase) = ExportManager(
        context = context,
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        audioEncryptionUtil = AudioEncryptionUtil(context)
    )

    private fun reflectionRepository(database: AppDatabase) = ReflectionRepository(
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        analyzer = LocalReflectionAnalyzer(),
        json = Json { ignoreUnknownKeys = true }
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
