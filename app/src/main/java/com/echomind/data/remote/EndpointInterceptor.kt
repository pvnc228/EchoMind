package com.echomind.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointInterceptor @Inject constructor(
    private val baseUrlProvider: BaseUrlProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val newUrl = baseUrlProvider.baseUrl.toHttpUrlOrNull() ?: return chain.proceed(original)
        val rebuilt = original.url.newBuilder()
            .scheme(newUrl.scheme)
            .host(newUrl.host)
            .port(newUrl.port)
            .build()
        return chain.proceed(original.newBuilder().url(rebuilt).build())
    }
}
