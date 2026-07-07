package com.echomind.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialsProvider: CredentialsProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val apiKey = credentialsProvider.apiKey
        if (apiKey.isBlank()) return chain.proceed(original)

        val request = original.newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .build()
        return chain.proceed(request)
    }
}
