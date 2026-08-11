package com.echomind.data.remote

import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlProvider @Inject constructor(
    private val remoteAccessPolicy: RemoteAccessPolicy
) {

    constructor() : this(RemoteAccessPolicy())

    val baseUrl: String
        get() = remoteAccessPolicy.endpoint()

    fun updateUrl(url: String) {
        remoteAccessPolicy.updateEndpoint(url)
    }

    fun effectiveUrl(apiPath: String): String {
        return remoteAccessPolicy.effectiveUrl(apiPath)
    }

    fun rewrite(original: HttpUrl): HttpUrl {
        return remoteAccessPolicy.rewrite(original)
    }

}
