package com.echomind.data.remote

import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import com.echomind.data.settings.SettingsStore
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class EndpointInterceptorTest {

    private val settingsStore: SettingsStore = mockk()

    @Test
    fun `approved destination change is rejected before proceed`() {
        val provider = BaseUrlProvider()
        provider.updateUrl("https://provider-a.example/api")
        val approvedDestination = "https://provider-a.example/api/v1/chat/completions"
        val request = Request.Builder()
            .url(approvedDestination)
            .header("X-EchoMind-Approved-Destination", approvedDestination)
            .build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        every { chain.request() } returns request
        coEvery { settingsStore.isLocalMode() } returns false
        val interceptor = EndpointInterceptor(provider, settingsStore)

        provider.updateUrl("https://provider-b.example/api")

        assertThrows(RemoteDestinationChangedException::class.java) {
            interceptor.intercept(chain)
        }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `approved destination uses the configured path prefix`() {
        val provider = BaseUrlProvider()
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
        coEvery { settingsStore.isLocalMode() } returns false
        val interceptor = EndpointInterceptor(provider, settingsStore)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.url.toString() == destination }) }
    }

    @Test
    fun `local mode enabled at the request boundary is rejected before proceed`() {
        val provider = BaseUrlProvider()
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
        val interceptor = EndpointInterceptor(provider, settingsStore)
        val localMode = AtomicBoolean(false)
        var reads = 0
        val checked = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        coEvery { settingsStore.isLocalMode() } coAnswers {
            if (++reads == 1) {
                checked.countDown()
                allowRead.await()
                false
            } else {
                localMode.get()
            }
        }
        val toggle = thread {
            checked.await()
            localMode.set(true)
            allowRead.countDown()
        }

        assertThrows(RemoteLocalModeChangedException::class.java) {
            interceptor.intercept(chain)
        }
        toggle.join()
        verify(exactly = 0) { chain.proceed(any()) }
    }
}
