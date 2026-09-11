package com.echomind.data.local.security

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioStartupCleanupTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `late startup callback cannot sweep files created after cleanup barrier`() {
        val context = mockk<Context>()
        every { context.cacheDir } returns folder.newFolder("cache")
        every { context.noBackupFilesDir } returns folder.newFolder("private")
        val capture = File(context.noBackupFilesDir, "capture_tmp").apply { mkdirs() }
        val oldAudio = File(capture, "old.m4a").apply { writeText("old orphan") }
        val oldPreview = File(context.cacheDir, "transcription_old.m4a").apply { writeText("old orphan") }
        val savedAudio = File(capture, "saved.m4a.enc").apply { writeText("persisted encrypted draft") }
        val util = AudioEncryptionUtil(context)

        // The producer crosses the startup barrier before opening its files.
        util.cleanupOrphanedTempFiles()
        assertFalse(oldAudio.exists())
        assertFalse(oldPreview.exists())
        val recording = File(capture, "active.m4a").apply { writeText("active recording") }
        val preview = File(context.cacheDir, "transcription_active.m4a").apply { writeText("active preview") }

        // Application's IO callback was delayed until after the producer started.
        util.cleanupOrphanedTempFiles()
        assertTrue("active recording survives late startup cleanup", recording.exists())
        assertTrue("active preview survives late startup cleanup", preview.exists())
        assertTrue("persisted encrypted draft survives cleanup", savedAudio.exists())
        assertTrue("background cleanup preserves the interruption notice", util.consumeInterruptedRecordingNotice())
        assertFalse("notice is consumed once", util.consumeInterruptedRecordingNotice())
    }
}
