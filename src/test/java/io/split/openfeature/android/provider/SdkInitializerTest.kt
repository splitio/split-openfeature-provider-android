package io.split.openfeature.android.provider

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.events.SplitEvent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SdkInitializerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val initializer = SplitSdkInitializer(testDispatcher)

    @Test
    fun `getReadyClient completes on SDK_READY`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = RecordingClient()
        every { factory.client(any() as Key) } returns client

        val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
        // Allow subscriptions to be registered before firing
        runCurrent()
        // After subscription occurs, simulate Split SDK firing SDK_READY
        client.fire(SplitEvent.SDK_READY)

        val result = deferred.await()
        assertSame(client, result)
        // Ensure we registered for events
        assertTrue(client.subscribed(SplitEvent.SDK_READY))
    }

    @Test(expected = IllegalStateException::class)
    fun `getReadyClient fails on SDK_READY_TIMED_OUT`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = RecordingClient()
        every { factory.client(any() as Key) } returns client

        val deferred = async { initializer.getReadyClient(factory, "key", timeoutMs = 5_000) }
        // Allow subscriptions to be registered before firing
        runCurrent()
        // Simulate timeout event
        client.fire(SplitEvent.SDK_READY_TIMED_OUT)
        // Await the result to surface the exception
        deferred.await()
    }

    @Test
    fun `initialize builds factory and returns ready client on SDK_READY`() = runTest(testDispatcher) {
        // Mock static SplitFactoryBuilder.build
        mockkStatic(SplitFactoryBuilder::class)
        val factory = mockk<SplitFactory>()
        every { SplitFactoryBuilder.build(any(), any(), any(), any()) } returns factory

        val client = RecordingClient()
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

        // Allow subscriptions to be registered before firing
        runCurrent()
        // Fire READY after subscriptions are in place
        client.fire(SplitEvent.SDK_READY)

        val (builtFactory, builtClient) = deferred.await()
        assertSame(factory, builtFactory)
        assertSame(client, builtClient)
    }

    @Test(expected = IllegalStateException::class)
    fun `initialize fails when SDK_READY_TIMED_OUT`() = runTest(testDispatcher) {
        mockkStatic(SplitFactoryBuilder::class)
        val factory = mockk<SplitFactory>()
        every { SplitFactoryBuilder.build(any(), any(), any(), any()) } returns factory

        val client = RecordingClient()
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

        // Allow subscriptions to be registered before firing
        runCurrent()
        // Simulate timeout
        client.fire(SplitEvent.SDK_READY_TIMED_OUT)
        deferred.await()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Minimal fake SplitClient that records event subscriptions and allows firing them.
     */
    private class RecordingClient : SplitClient by mockk(relaxed = true) {
        private val listeners = mutableMapOf<SplitEvent, MutableList<(SplitClient?) -> Unit>>()

        override fun on(event: SplitEvent?, task: io.split.android.client.events.SplitEventTask?) {
            if (event != null && task != null) {
                listeners.getOrPut(event) { mutableListOf() }.add { c -> task.onPostExecution(c) }
            }
        }

        fun fire(event: SplitEvent) {
            listeners[event]?.forEach { it(this) }
        }

        fun subscribed(event: SplitEvent): Boolean = listeners.containsKey(event)
    }
}
