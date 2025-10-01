package io.split.openfeature.android.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SplitSdkDelegateTest : BaseMockkTest() {

    private val testDispatcher = StandardTestDispatcher()
    private val initializer = SplitSdkDelegate(testDispatcher, SplitEventsRegistry())

    @Test
    fun `getReadyClient completes on SDK_READY_FROM_CACHE`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = TestHelperClient()
        every { factory.client(any() as Key) } returns client

        val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
        // This allows the SDK event listeners to be attached first
        runCurrent()
        // Simulate SDK READY FROM CACHE
        client.fire(SplitEvent.SDK_READY_FROM_CACHE)

        val result = deferred.await()
        assertSame(client, result)
        assertTrue(client.subscribed(SplitEvent.SDK_READY_FROM_CACHE))
    }

    @Test
    fun `getReadyClient completes on SDK_READY_TIMED_OUT`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = TestHelperClient()
        every { factory.client(any() as Key) } returns client

        val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
        // This allows the SDK event listeners to be attached first
        runCurrent()
        // Simulate SDK READY TIMED OUT
        client.fire(SplitEvent.SDK_READY_TIMED_OUT)

        val result = deferred.await()
        assertSame(client, result)
        assertTrue(client.subscribed(SplitEvent.SDK_READY_TIMED_OUT))
    }

    @Test(expected = CancellationException::class)
    fun `initialize propagates cancellation`() = runTest(testDispatcher) {
        mockkStatic(SplitFactoryBuilder::class)
        val factory = mockk<SplitFactory>()
        every { SplitFactoryBuilder.build(any(), any(), any(), any()) } returns factory

        val client = TestHelperClient()
        every { factory.client(any() as Key) } returns client

        val appContext: Context = ApplicationProvider.getApplicationContext()

        val job = async {
            initializer.initialize(
                appContext = appContext,
                sdkKey = "sdk-key",
                targetingKey = "target",
                timeoutMs = 5_000
            )
        }

        // This allows the SDK event listener to be attached first
        runCurrent()
        job.cancel()
        job.await() // surfaces CancellationException
    }

    @Test
    fun `initialize builds factory and returns ready client on SDK_READY`() =
        runTest(testDispatcher) {
            mockkStatic(SplitFactoryBuilder::class)
            val factory = mockk<SplitFactory>()
            every { SplitFactoryBuilder.build(any(), any(), any(), any()) } returns factory

            val client = TestHelperClient()
            every { factory.client(any() as Key) } returns client

            val appContext: Context = ApplicationProvider.getApplicationContext()

            val deferred = async {
                initializer.initialize(
                    appContext = appContext,
                    sdkKey = "sdk-key",
                    targetingKey = "target",
                    timeoutMs = 5_000
                )
            }

            // This allows the SDK event listeners to be attached first
            runCurrent()
            // Simulate SDK_READY_FROM_CACHE
            client.fire(SplitEvent.SDK_READY_FROM_CACHE)

            val (builtFactory, builtClient) = deferred.await()
            assertSame(factory, builtFactory)
            assertSame(client, builtClient)
        }

    @Test
    fun `client becomes ready after isReady check but before listener registration`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>(relaxed = true)

        every { client.isReady } returns false

        every { client.on(any<SplitEvent>(), any<SplitEventTask>()) } answers {
            every { client.isReady } returns true
        }

        every { factory.client(any() as Key) } returns client

        val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
        runCurrent()

        val result = deferred.await()

        assertSame(client, result)
    }
}
