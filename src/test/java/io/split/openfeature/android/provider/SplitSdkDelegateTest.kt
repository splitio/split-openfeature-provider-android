package io.split.openfeature.android.provider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.events.SplitEvent
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
    private val initializer = SplitSdkDelegate(testDispatcher)

    @Test
    fun `getReadyClient completes on SDK_READY`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = TestHelperClient()
        every { factory.client(any() as Key) } returns client

        val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
        // This allows the SDK event listeners to be attached first
        runCurrent()
        // Simulate SDK READY
        client.fire(SplitEvent.SDK_READY)

        val result = deferred.await()
        assertSame(client, result)
        assertTrue(client.subscribed(SplitEvent.SDK_READY))
    }

    @Test(expected = TimeoutCancellationException::class)
    fun `getReadyClient times out when READY not received`() =
        runTest(testDispatcher) {
            val factory = mockk<SplitFactory>()
            val client = TestHelperClient()
            every { factory.client(any() as Key) } returns client

            val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
            // This allows the SDK event listeners to be attached first
            runCurrent()
            // Do not fire any READY event; advance virtual time past timeout
            advanceTimeBy(5_000)
            runCurrent()
            // Await the result to surface TimeoutCancellationException
            deferred.await()
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
            // Simulate SDK_READY
            client.fire(SplitEvent.SDK_READY)

            val (builtFactory, builtClient) = deferred.await()
            assertSame(factory, builtFactory)
            assertSame(client, builtClient)
        }

    @Test(expected = TimeoutCancellationException::class)
    fun `initialize times out when READY not received`() =
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
            // Do not fire any READY event; advance virtual time past timeout
            advanceTimeBy(5_000)
            runCurrent()
            deferred.await()
        }
}
