package com.echomind.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlProvider @Inject constructor() {
    private val destinationLock = ReentrantReadWriteLock()

    @Volatile
    var baseUrl: String = "http://localhost:1234/"
        private set

    fun updateUrl(url: String) {
        destinationLock.writeLock().withLock {
            baseUrl = url.trimEnd('/') + "/"
        }
    }

    fun effectiveUrl(apiPath: String): String {
        return destinationLock.readLock().withLock {
            val configured = baseUrl.toHttpUrlOrNull() ?: return@withLock baseUrl + apiPath.trimStart('/')
            configured.newBuilder()
                .encodedPath(joinPath(configured.encodedPath, apiPath))
                .build()
                .toString()
        }
    }

    fun rewrite(original: HttpUrl): HttpUrl {
        return destinationLock.readLock().withLock {
            val configured = baseUrl.toHttpUrlOrNull() ?: return@withLock original
            original.newBuilder()
                .scheme(configured.scheme)
                .host(configured.host)
                .port(configured.port)
                .encodedPath(joinPath(configured.encodedPath, original.encodedPath))
                .build()
        }
    }

    fun <T> withStableDestination(block: () -> T): T =
        destinationLock.readLock().withLock(block)

    private fun joinPath(prefix: String, suffix: String): String {
        val normalizedPrefix = prefix.trimEnd('/')
        val normalizedSuffix = suffix.trimStart('/')
        return "/" + listOf(normalizedPrefix.trimStart('/'), normalizedSuffix)
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}
