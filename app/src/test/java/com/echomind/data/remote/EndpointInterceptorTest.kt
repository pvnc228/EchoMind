package com.echomind.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointInterceptorTest {

    @Test
    fun `approved destination change is rejected before proceed`() {
        val policy = RemoteAccessPolicy()
        val provider = BaseUrlProvider(policy)
        provider.updateUrl("https://provider-a.example/api")
        val approvedDestination = "https://provider-a.example/api/v1/chat/completions"
        val request = Request.Builder()
            .url(approvedDestination)
            .header("X-EchoMind-Approved-Destination", approvedDestination)
            .build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        policy.updateLocalMode(false)
        every { chain.call() } returns mockk(relaxed = true)
        val interceptor = EndpointInterceptor(provider, policy)

        provider.updateUrl("https://provider-b.example/api")

        assertThrows(RemoteDestinationChangedException::class.java) {
            interceptor.intercept(chain)
        }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `approved header cannot authorize a different actual request URL`() {
        val policy = RemoteAccessPolicy()
        val provider = BaseUrlProvider(policy)
        provider.updateUrl("https://provider-b.example/api")
        policy.updateLocalMode(false)
        val approvedDestination = "https://provider-b.example/api/v1/chat/completions"
        val request = Request.Builder()
            .url("https://provider-c.example/api/v1/chat/completions")
            .header(APPROVED_DESTINATION_HEADER, approvedDestination)
            .build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.call() } returns mockk(relaxed = true)
        val interceptor = EndpointInterceptor(provider, policy)

        assertThrows(RemoteDestinationChangedException::class.java) {
            interceptor.intercept(chain)
        }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `approved destination uses the configured path prefix`() {
        val policy = RemoteAccessPolicy()
        val provider = BaseUrlProvider(policy)
        provider.updateUrl("https://provider.example/api")
        val destination = provider.effectiveUrl("v1/chat/completions")
        val request = Request.Builder()
            .url(destination)
            .header("X-EchoMind-Approved-Destination", destination)
            .build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        val response = mockk<Response>()
        every { chain.proceed(any()) } returns response
        policy.updateLocalMode(false)
        every { chain.call() } returns mockk(relaxed = true)
        val interceptor = EndpointInterceptor(provider, policy)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.url.toString() == destination }) }
    }

    @Test
    fun `local mode enabled at the request boundary is rejected before proceed`() {
        val policy = RemoteAccessPolicy()
        val provider = BaseUrlProvider(policy)
        provider.updateUrl("https://provider.example/api")
        val destination = provider.effectiveUrl("v1/chat/completions")
        val request = Request.Builder()
            .url(destination)
            .header("X-EchoMind-Approved-Destination", destination)
            .build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        val response = mockk<Response>()
        every { chain.proceed(any()) } returns response
        policy.updateLocalMode(true)
        every { chain.call() } returns mockk(relaxed = true)
        val interceptor = EndpointInterceptor(provider, policy)
        assertThrows(RemoteLocalModeChangedException::class.java) {
            interceptor.intercept(chain)
        }
        verify(exactly = 0) { chain.proceed(any()) }
    }
}
