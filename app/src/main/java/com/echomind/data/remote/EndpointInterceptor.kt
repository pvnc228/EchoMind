package com.echomind.data.remote

import com.echomind.data.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointInterceptor @Inject constructor(
    private val baseUrlProvider: BaseUrlProvider,
    private val settingsStore: SettingsStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val approvedDestination = original.header(APPROVED_DESTINATION_HEADER)
        if (approvedDestination != null) {
            return baseUrlProvider.withStableDestination {
                if (runBlocking { settingsStore.isLocalMode() }) {
                    throw RemoteLocalModeChangedException()
                }
                val currentDestination = baseUrlProvider.effectiveUrl(QUESTION_API_PATH)
                if (approvedDestination != original.url.toString() ||
                    approvedDestination != currentDestination
                ) {
                    throw RemoteDestinationChangedException()
                }
                if (runBlocking { settingsStore.isLocalMode() }) {
                    throw RemoteLocalModeChangedException()
                }
                chain.proceed(
                    original.newBuilder()
                        .removeHeader(APPROVED_DESTINATION_HEADER)
                        .build()
                )
            }
        }

        return chain.proceed(original.newBuilder().url(baseUrlProvider.rewrite(original.url)).build())
    }
}
