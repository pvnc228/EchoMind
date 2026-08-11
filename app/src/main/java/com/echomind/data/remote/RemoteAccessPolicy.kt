package com.echomind.data.remote

import com.echomind.data.settings.StoredSettings
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.IdentityHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared, synchronous state for remote settings and the network-start boundary.
 * The lock covers only state transitions and permit validation; the network response runs outside it.
 */
@Singleton
class RemoteAccessPolicy @Inject constructor() {
    private val lock = ReentrantLock()
    private val activeCalls = IdentityHashMap<Call, Permit>()
    private var endpoint = DEFAULT_ENDPOINT
    private var endpointOverride: String? = null
    private var persistedLocalMode = true
    private var localModeOverride: Boolean? = null
    private var revision = 0L

    fun hydratePersisted(settings: StoredSettings) {
        lock.withLock {
            val normalizedEndpoint = normalizeEndpoint(settings.apiEndpoint)
            val previousEndpoint = endpoint
            val previousLocalMode = localModeLocked()
            if (endpointOverride == null) {
                endpoint = normalizedEndpoint
            }
            persistedLocalMode = settings.localMode
            val endpointChanged = endpoint != previousEndpoint
            val localModeChanged = localModeLocked() != previousLocalMode
            if (endpointChanged || localModeChanged) {
                revision++
                cancelActiveCallsLocked()
            }
        }
    }

    fun updateEndpoint(url: String) {
        lock.withLock {
            val normalized = normalizeEndpoint(url)
            endpointOverride = normalized
            if (endpoint == normalized) return
            endpoint = normalized
            revision++
            cancelActiveCallsLocked()
        }
    }

    fun updateLocalMode(enabled: Boolean) {
        lock.withLock {
            if (localModeOverride == enabled) return
            localModeOverride = enabled
            revision++
            cancelActiveCallsLocked()
        }
    }

    fun isLocalMode(): Boolean = lock.withLock { localModeLocked() }

    fun endpoint(): String = lock.withLock { endpoint }

    fun effectiveUrl(apiPath: String): String = lock.withLock {
        effectiveUrlLocked(apiPath)
    }

    fun rewrite(original: HttpUrl): HttpUrl = lock.withLock {
        val configured = endpoint.toHttpUrlOrNull() ?: return@withLock original
        original.newBuilder()
            .scheme(configured.scheme)
            .host(configured.host)
            .port(configured.port)
            .encodedPath(joinPath(configured.encodedPath, original.encodedPath))
            .build()
    }

    /**
     * Reserves and validates the exact request. A setting change before commit invalidates the
     * permit; a change after commit cancels the active OkHttp call without holding this lock
     * through the network response.
     */
    fun <T> startApprovedRequest(
        call: Call,
        expectedDestination: String,
        actualDestination: String,
        apiPath: String,
        beforeNetworkStart: (() -> Unit)? = null,
        block: () -> T
    ): T {
        val permit = lock.withLock {
            if (localModeLocked()) throw RemoteLocalModeChangedException()
            if (actualDestination != expectedDestination) {
                throw RemoteDestinationChangedException()
            }
            if (effectiveUrlLocked(apiPath) != expectedDestination) {
                throw RemoteDestinationChangedException()
            }
            Permit(revision = revision, endpoint = endpoint).also {
                activeCalls[call] = it
            }
        }
        try {
            validatePermit(permit)
            beforeNetworkStart?.invoke()
            commitPermit(permit)
            return block()
        } finally {
            lock.withLock {
                if (activeCalls[call] === permit) activeCalls.remove(call)
            }
        }
    }

    private fun validatePermit(permit: Permit) {
        lock.withLock {
            validatePermitLocked(permit)
        }
    }

    private fun commitPermit(permit: Permit) {
        lock.withLock {
            validatePermitLocked(permit)
        }
    }

    private fun validatePermitLocked(permit: Permit) {
        if (localModeLocked()) throw RemoteLocalModeChangedException()
        if (permit.revision != revision || permit.endpoint != endpoint) {
            throw RemoteDestinationChangedException()
        }
    }

    private fun cancelActiveCallsLocked() {
        activeCalls.keys.toList().forEach(Call::cancel)
    }

    private fun localModeLocked(): Boolean = localModeOverride ?: persistedLocalMode

    private fun effectiveUrlLocked(apiPath: String): String {
        val configured = endpoint.toHttpUrlOrNull()
            ?: return endpoint + apiPath.trimStart('/')
        return configured.newBuilder()
            .encodedPath(joinPath(configured.encodedPath, apiPath))
            .build()
            .toString()
    }

    private fun normalizeEndpoint(url: String): String = url.trimEnd('/') + "/"

    private fun joinPath(prefix: String, suffix: String): String {
        val normalizedPrefix = prefix.trimEnd('/')
        val normalizedSuffix = suffix.trimStart('/')
        return "/" + listOf(normalizedPrefix.trimStart('/'), normalizedSuffix)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    private data class Permit(
        val revision: Long,
        val endpoint: String
    )

    private companion object {
        const val DEFAULT_ENDPOINT = "http://localhost:1234/"
    }
}
