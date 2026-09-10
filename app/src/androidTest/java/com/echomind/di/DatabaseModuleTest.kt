package com.echomind.di

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.PassphraseProvider
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseModuleTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        val prefix = "dbtest_${UUID.randomUUID()}_"
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(prefix + name)
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
    }

    @Test
    fun productionFactoryMustOpenExistingDatabaseWithStoredKey() {
        val isolated = IsolatedContext(context)
        val provider = PassphraseProvider(isolated)
        val expectedKey = provider.getPassphrase()
        val seeded = Room.databaseBuilder(isolated, AppDatabase::class.java, "echomind.db")
            .openHelperFactory(SupportFactory(expectedKey.copyOf()))
            .build()
        try {
            seeded.openHelper.writableDatabase
        } finally {
            seeded.close()
        }

        val production = DatabaseModule.provideAppDatabase(isolated, provider)
        try {
            val opened = runCatching { production.openHelper.writableDatabase }.isSuccess
            assertTrue("Production factory must open existing database with stored key", opened)
        } finally {
            production.close()
            context.deleteDatabase(isolated.prefix + "echomind.db")
            context.deleteSharedPreferences(isolated.prefix + "echomind_db_passphrase")
            expectedKey.fill(0)
        }
    }

    @Test
    fun productionFreshDatabaseMustNotUseAllZeroKey() {
        val isolated = IsolatedContext(context)
        val production = DatabaseModule.provideAppDatabase(isolated, PassphraseProvider(isolated))
        try {
            production.openHelper.writableDatabase
        } finally {
            production.close()
        }

        val zeroKeyDb = Room.databaseBuilder(isolated, AppDatabase::class.java, "echomind.db")
            .openHelperFactory(SupportFactory(ByteArray(32)))
            .build()
        try {
            val zeroOpens = runCatching { zeroKeyDb.openHelper.writableDatabase }.isSuccess
            assertFalse("Production database must not be encrypted with all-zero key", zeroOpens)
        } finally {
            zeroKeyDb.close()
            context.deleteDatabase(isolated.prefix + "echomind.db")
            context.deleteSharedPreferences(isolated.prefix + "echomind_db_passphrase")
        }
    }

    @Test
    fun legacyZeroKeyDatabaseMustBeRecoveredAndRekeyedToStoredKey() {
        val isolated = IsolatedContext(context)
        val provider = PassphraseProvider(isolated)
        val expectedKey = provider.getPassphrase()

        // Seed a legacy database created with 32 zero bytes
        val zeroKeyDb = Room.databaseBuilder(isolated, AppDatabase::class.java, "echomind.db")
            .openHelperFactory(SupportFactory(ByteArray(32)))
            .build()
        try {
            zeroKeyDb.openHelper.writableDatabase
        } finally {
            zeroKeyDb.close()
        }

        // provideAppDatabase must automatically detect zero-key and rekey to expectedKey
        val production = DatabaseModule.provideAppDatabase(isolated, provider)
        try {
            val result = runCatching { production.openHelper.writableDatabase }
            if (result.isFailure) {
                android.util.Log.e("DatabaseModuleTest", "Error opening database", result.exceptionOrNull())
            }
            assertTrue("Production must recover and open legacy zero-key database: ${result.exceptionOrNull()?.message}", result.isSuccess)
        } finally {
            production.close()
        }

        // Now verify it can NO LONGER be opened with all-zero key
        val zeroKeyCheck = Room.databaseBuilder(isolated, AppDatabase::class.java, "echomind.db")
            .openHelperFactory(SupportFactory(ByteArray(32)))
            .build()
        try {
            val zeroOpens = runCatching { zeroKeyCheck.openHelper.writableDatabase }.isSuccess
            assertFalse("Recovered database must no longer open with zero key", zeroOpens)
        } finally {
            zeroKeyCheck.close()
            context.deleteDatabase(isolated.prefix + "echomind.db")
            context.deleteSharedPreferences(isolated.prefix + "echomind_db_passphrase")
            expectedKey.fill(0)
        }
    }
}
