package com.echomind.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.repository.ReflectionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipFile

class ExportManagerTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun zipContainsTheConfirmedReflectionAndItsStableProvenance() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: java.io.File? = null

        try {
            val repository = ReflectionRepository(
                database = database,
                entryDao = database.entryDao(),
                knowledgeDao = database.knowledgeDao(),
                analyzer = LocalReflectionAnalyzer(),
                json = Json { ignoreUnknownKeys = true }
            )
            runBlocking {
                val rawRecordId = repository.captureRawText("My original words")
                val proposal = repository.createLocalProposal(rawRecordId)
                repository.confirm(proposal.hypothesisId, "My confirmed conclusion")

                exportFile = ExportManager(
                    context = context,
                    database = database,
                    entryDao = database.entryDao(),
                    knowledgeDao = database.knowledgeDao(),
                    audioEncryptionUtil = AudioEncryptionUtil(context)
                ).exportToZip().getOrThrow()
            }

            val manifest = ZipFile(requireNotNull(exportFile)).use { zip ->
                val entry = requireNotNull(zip.getEntry("manifest.json"))
                val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Json.decodeFromString<ExportManifest>(content)
            }

            assertEquals(4, manifest.version)
            assertEquals("My original words", manifest.rawRecords.single().originalText)
            assertEquals("confirmed", manifest.hypotheses.single().status)
            assertEquals("My confirmed conclusion", manifest.revisions.single().text)
            assertEquals(
                manifest.rawRecords.single().id,
                manifest.evidenceLinks.single().sourceRawRecordId
            )
            assertTrue(manifest.entries.single().analysisStatus == "legacy_unconfirmed")
        } finally {
            database.close()
            exportFile?.delete()
        }
    }
}
