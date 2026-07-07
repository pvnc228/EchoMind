package com.echomind.data.remote

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlProvider @Inject constructor() {
    @Volatile
    var baseUrl: String = "http://localhost:1234/"
        private set

    fun updateUrl(url: String) {
        baseUrl = url.trimEnd('/') + "/"
    }
}
