package com.echomind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.local.security.PassphraseProvider
import com.echomind.data.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * M7 release-evidence profile. Each measurement goes through a production seam:
 * the SQLCipher passphrase provider, the AudioEncryptionUtil (AES256-GCM-HKDF), and the
 * public KnowledgeRepository retrieval paths. Values are printed for the record; the
 * assertions are sanity checks only (operation completes and returns the seeded data),
 * not performance budgets, because this artifact records the reference-runtime profile
 * rather than claiming a release latency guarantee.
 */
class ReleaseProfileBenchmarkTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun encryptedDatabaseOpenAndFirstQueryProfile() {
        val dbName = "profile-encrypted-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        val passphraseProvider = PassphraseProvider(context)
        val factory = SupportFactory(passphraseProvider.getPassphrase())

        var database: AppDatabase? = null
        try {
            // Seed then close so the cold-open measurement includes the real SQLCipher open.
            database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .build()
            val dao: KnowledgeDao = database!!.knowledgeDao()
            val seedNanos = measureNanoTime {
                runBlocking {
                    dao.insertRawRecords((1L..1_000L).map { id ->
                        RawRecordEntity(
                            id = id,
                            originalText = "profile record $id",
                            audioPath = null,
                            durationMs = 0L,
                            createdAt = id
                        )
                    })
                }
            }
            database!!.close()

            var coldOpenMillis = 0L
            val coldOpenNanos = measureNanoTime {
                // SQLCipher clears the passphrase after first open; a real reopen uses a fresh
                // SupportFactory reading the persisted passphrase again, matching the app lifecycle.
                val reopenFactory = SupportFactory(passphraseProvider.getPassphrase())
                database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .openHelperFactory(reopenFactory)
                    .build()
                val reopened = database!!.knowledgeDao()
                runBlocking { reopened.getAllRawRecords() }
            }
            coldOpenMillis = coldOpenNanos / 1_000_000
            assertEquals(1_000, runBlocking { database!!.knowledgeDao().getAllRawRecords() }.size)
            println(
                "ENCRYPTED_DB_PROFILE seed1000Ms=${seedNanos / 1_000_000} " +
                    "coldOpenAndFirstQueryMs=$coldOpenMillis"
            )
            assertTrue(database!!.isOpen)
        } finally {
            database?.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun audioEncryptionProfile() {
        val util = AudioEncryptionUtil(context)
        val plaintext = File(context.cacheDir, "profile_${System.nanoTime()}.wav")
        val encrypted = File(context.cacheDir, "profile_${System.nanoTime()}.wav.enc")
        try {
            plaintext.writeBytes(ByteArray(512 * 1024) { (it % 251).toByte() })
            val encryptNanos = measureNanoTime { util.encryptFile(plaintext, encrypted) }
            val decryptNanos = measureNanoTime { util.decryptToTempFile(encrypted.absolutePath) }
            println(
                "AUDIO_ENCRYPT_PROFILE 512KB encryptMs=${encryptNanos / 1_000_000} " +
                    "decryptMs=${decryptNanos / 1_000_000}"
            )
            assertTrue(encrypted.exists() && encrypted.length() > 0L)
        } finally {
            plaintext.delete()
            encrypted.delete()
        }
    }

    @Test
    fun longHistoryRetrievalProfile() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.knowledgeDao()
            runBlocking {
                dao.insertRawRecords((1L..10_000L).map { id ->
                    RawRecordEntity(
                        id = id,
                        originalText = "career project decision evidence archive $id",
                        audioPath = null,
                        durationMs = 0L,
                        createdAt = id
                    )
                })
                dao.insertConclusions(
                    listOf(
                        ConclusionEntity(id = 1L, rawRecordId = 1L, currentRevisionId = 1L, createdAt = 1L)
                    )
                )
                dao.insertRevisions(
                    listOf(
                        ConclusionRevisionEntity(
                            id = 1L,
                            conclusionId = 1L,
                            version = 1,
                            text = "career project decision evidence",
                            author = "user",
                            createdAt = 1L
                        )
                    )
                )
            }

            val repository = KnowledgeRepository(
                database = database,
                knowledgeDao = dao,
                settingsStore = SettingsStore(context)
            )

            val searchNanos = measureNanoTime {
                runBlocking { repository.search("career") }
            }
            val linkCandidatesNanos = measureNanoTime {
                runBlocking { repository.getLinkCandidates(currentRevisionId = 1L) }
            }
            val homeNanos = measureNanoTime {
                runBlocking { repository.getHomeRelevance() }
            }
            println(
                "LONG_HISTORY_PROFILE 10k searchMs=${searchNanos / 1_000_000} " +
                    "linkCandidatesMs=${linkCandidatesNanos / 1_000_000} " +
                    "homeRelevanceMs=${homeNanos / 1_000_000}"
            )
            val searchResult = runBlocking { repository.search("career") }
            assertTrue(searchResult.isNotEmpty())
        } finally {
            database.close()
        }
    }
}
