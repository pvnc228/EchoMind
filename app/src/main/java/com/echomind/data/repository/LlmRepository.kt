package com.echomind.data.repository

import com.echomind.data.analysis.SimpleTextAnalyzer
import com.echomind.data.remote.LlmApi
import com.echomind.data.remote.dto.Message
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.Entry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository @Inject constructor(
    private val llmApi: LlmApi,
    private val offlineAnalyzer: SimpleTextAnalyzer,
    private val settingsStore: SettingsStore
) {
    suspend fun transcribeAudio(audioFile: java.io.File): Result<String> {
        return remoteRawContentBlocked()
    }

    suspend fun analyzeEntry(entry: Entry): Result<Entry> {
        return Result.success(offlineAnalyzer.analyze(entry))
    }

    suspend fun askQuestion(messages: List<Message>): Result<String> {
        return remoteRawContentBlocked()
    }

    private fun <T> networkDisabled(): Result<T> =
        Result.failure(AiNetworkDisabledException())

    private suspend fun <T> remoteRawContentBlocked(): Result<T> =
        if (settingsStore.isLocalMode()) {
            networkDisabled()
        } else {
            Result.failure(RemoteApprovalRequiredException())
        }
}

class AiNetworkDisabledException : IllegalStateException(
    "AI network access is disabled while local mode is on"
)

class RemoteApprovalRequiredException : IllegalStateException(
    "Raw personal content cannot be sent remotely. A minimized preview and per-request approval are required."
)
