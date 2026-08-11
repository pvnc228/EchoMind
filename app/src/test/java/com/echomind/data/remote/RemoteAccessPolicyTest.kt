package com.echomind.data.remote

import com.echomind.data.settings.StoredSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class RemoteAccessPolicyTest {

    @Test
    fun `local mode toggled after allowed observation prevents network start`() {
        val policy = RemoteAccessPolicy()
        policy.hydratePersisted(StoredSettings("https://provider.example/api", localMode = false))
        val call = mockk<Call>(relaxed = true)
        val proceedCalled = AtomicBoolean(false)
        val observed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val toggle = thread {
            observed.await()
            policy.updateLocalMode(true)
            release.countDown()
        }

        assertThrows(RemoteLocalModeChangedException::class.java) {
            policy.startApprovedRequest(
                call = call,
                expectedDestination = policy.effectiveUrl(QUESTION_API_PATH),
                actualDestination = policy.effectiveUrl(QUESTION_API_PATH),
                apiPath = QUESTION_API_PATH,
                beforeNetworkStart = {
                    observed.countDown()
                    release.await()
                }
            ) {
                proceedCalled.set(true)
                Unit
            }
        }
        toggle.join()

        assertFalse(proceedCalled.get())
        verify(exactly = 1) { call.cancel() }
    }

    @Test
    fun `local mode toggle after network start cancels active call without holding policy lock`() {
        val policy = RemoteAccessPolicy()
        policy.hydratePersisted(StoredSettings("https://provider.example/api", localMode = false))
        val call = mockk<Call>(relaxed = true)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val request = thread {
            policy.startApprovedRequest(
                call = call,
                expectedDestination = policy.effectiveUrl(QUESTION_API_PATH),
                actualDestination = policy.effectiveUrl(QUESTION_API_PATH),
                apiPath = QUESTION_API_PATH
            ) {
                started.countDown()
                release.await()
                Unit
            }
        }

        started.await()
        policy.updateLocalMode(true)
        verify(exactly = 1) { call.cancel() }
        release.countDown()
        request.join()
    }

    @Test
    fun `persisted endpoint hydrates a fresh provider and approved request uses its exact path`() {
        val policy = RemoteAccessPolicy()
        val provider = BaseUrlProvider(policy)
        val persisted = StoredSettings("https://provider-b.example/api", localMode = false)
        policy.hydratePersisted(persisted)
        val destination = "https://provider-b.example/api/v1/chat/completions"
        assertEquals(destination, provider.effectiveUrl(QUESTION_API_PATH))

        val request = Request.Builder()
            .url(destination)
            .header(APPROVED_DESTINATION_HEADER, destination)
            .build()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        val response = mockk<Response>()
        every { chain.request() } returns request
        every { chain.call() } returns mockk(relaxed = true)
        every { chain.proceed(any()) } returns response
        val interceptor = EndpointInterceptor(provider, policy)

        interceptor.intercept(chain)

        verify { chain.proceed(match { it.url.toString() == destination }) }
    }

    @Test
    fun `stale persisted load under endpoint override does not cancel an active call`() {
        val policy = RemoteAccessPolicy()

        policy.hydratePersisted(StoredSettings("https://provider-a.example/api", localMode = false))
        policy.updateEndpoint("https://provider-b.example/api")
        val call = mockk<Call>(relaxed = true)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val request = thread {
            policy.startApprovedRequest(
                call = call,
                expectedDestination = policy.effectiveUrl(QUESTION_API_PATH),
                actualDestination = policy.effectiveUrl(QUESTION_API_PATH),
                apiPath = QUESTION_API_PATH
            ) {
                started.countDown()
                release.await()
                Unit
            }
        }

        started.await()
        policy.hydratePersisted(StoredSettings("https://provider-a.example/api", localMode = false))

        assertEquals(
            "https://provider-b.example/api/v1/chat/completions",
            policy.effectiveUrl(QUESTION_API_PATH)
        )
        verify(exactly = 0) { call.cancel() }
        release.countDown()
        request.join()
    }
}
