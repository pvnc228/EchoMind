package com.echomind.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EndpointInterceptor @Inject constructor(
    private val baseUrlProvider: BaseUrlProvider,
    private val remoteAccessPolicy: RemoteAccessPolicy
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val approvedDestination = original.header(APPROVED_DESTINATION_HEADER)
        if (approvedDestination != null) {
            return remoteAccessPolicy.startApprovedRequest(
                call = chain.call(),
                expectedDestination = approvedDestination,
                apiPath = QUESTION_API_PATH
            ) {
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
