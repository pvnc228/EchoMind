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
 * public KnowledgeRepository retrieval paths. Reported values are medians of several
 * samples; the assertions are sanity checks only (operation completes and returns the
 * seeded data), not performance budgets, because this artifact records the
 * reference-runtime profile rather than claiming a release latency guarantee.
 */
class ReleaseProfileBenchmarkTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun encryptedDatabaseOpenAndFirstQueryProfile() {
        val dbName = "profile-encrypted-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        val passphraseProvider = PassphraseProvider(context)

        var database: AppDatabase? = null
        try {
            database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .openHelperFactory(SupportFactory(passphraseProvider.getPassphrase()))
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

            // SQLCipher clears the passphrase after first open; a real reopen uses a fresh
            // SupportFactory reading the persisted passphrase again, matching the app lifecycle.
            val coldOpenMillis = medianMillis(samples = 3) {
                database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .openHelperFactory(SupportFactory(passphraseProvider.getPassphrase()))
                    .build()
                val reopened = database!!.knowledgeDao()
                runBlocking { reopened.getAllRawRecords() }
                database!!.close()
            }

            database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .openHelperFactory(SupportFactory(passphraseProvider.getPassphrase()))
                .build()
            assertEquals(1_000, runBlocking { database!!.knowledgeDao().getAllRawRecords() }.size)
            println(
                "ENCRYPTED_DB_PROFILE seed1000Ms=${seedNanos / 1_000_000} " +
                    "coldOpenAndFirstQueryMedianMs=$coldOpenMillis"
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
        val data = ByteArray(512 * 1024) { (it % 251).toByte() }

        val encryptMillis = medianMillis(samples = 5) {
            val plain = File(context.cacheDir, "profile_enc_${System.nanoTime()}.wav")
            val encrypted = File(context.cacheDir, "profile_enc_${System.nanoTime()}.wav.enc")
            try {
                plain.writeBytes(data)
                util.encryptFile(plain, encrypted)
            } finally {
                plain.delete()
                encrypted.delete()
            }
        }

        val plaintext = File(context.cacheDir, "profile_dec_${System.nanoTime()}.wav")
        val encrypted = File(context.cacheDir, "profile_dec_${System.nanoTime()}.wav.enc")
        try {
            plaintext.writeBytes(data)
            util.encryptFile(plaintext, encrypted)
            val decryptMillis = medianMillis(samples = 5) {
                val temp = util.decryptToTempFile(encrypted.absolutePath)
                util.deleteTempFile(temp.absolutePath)
            }
            println(
                "AUDIO_ENCRYPT_PROFILE 512KB encryptMedianMs=$encryptMillis " +
                    "decryptMedianMs=$decryptMillis"
            )
        } finally {
            plaintext.delete()
            encrypted.delete()
        }
    }

    @Test
    fun longHistoryRetrievalProfile() {
        // Plain in-memory Room, not SQLCipher: isolates retrieval/ranking cost from the
        // encrypted-I/O cost measured by the separate encrypted-DB profile.
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

            val searchMillis = medianMillis(samples = 5) {
                runBlocking { repository.search("career") }
            }
            val linkCandidatesMillis = medianMillis(samples = 5) {
                runBlocking { repository.getLinkCandidates(currentRevisionId = 1L) }
            }
            val homeMillis = medianMillis(samples = 5) {
                runBlocking { repository.getHomeRelevance() }
            }
            println(
                "LONG_HISTORY_PROFILE 10k searchMedianMs=$searchMillis " +
                    "linkCandidatesMedianMs=$linkCandidatesMillis " +
                    "homeRelevanceMedianMs=$homeMillis"
            )
            val searchResult = runBlocking { repository.search("career") }
            assertTrue(searchResult.isNotEmpty())
        } finally {
            database.close()
        }
    }

    private fun medianMillis(samples: Int, block: () -> Unit): Long {
        require(samples >= 1) { "At least one sample is required." }
        val nanos = List(samples) { measureNanoTime { block() } }.sorted()
        return nanos[samples / 2] / 1_000_000
    }
}
