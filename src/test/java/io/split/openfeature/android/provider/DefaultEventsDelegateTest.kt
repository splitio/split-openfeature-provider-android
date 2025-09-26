package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import io.mockk.CapturingSlot
import io.split.android.client.SplitClient
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEventsDelegateTest {

    private lateinit var registry: SplitEventsRegistry
    private lateinit var stateRef: AtomicReference<SplitProviderState>

    @Before
    fun setUp() {
        registry = SplitEventsRegistry()
        stateRef = AtomicReference(SplitProviderState())
    }


    @Test
    fun `observe emits ProviderReady when SDK_READY is fired`() = runTest(StandardTestDispatcher()) {
        val client = mockk<SplitClient>(relaxed = true)
        every { client.isReady } returns false
        val listeners = captureSplitListeners(client)

        stateRef.set(SplitProviderState(splitClient = client))
        val delegate = DefaultEventsDelegate(stateRef, registry)

        val job = async { withTimeout(2_000) { delegate.observe().first() } }
        // Ensure the collector is started before firing the event
        runCurrent()

        // Trigger SDK_READY
        listeners.readyTask.captured.onPostExecution(client)

        assertEquals(OpenFeatureProviderEvents.ProviderReady, job.await())
    }

    @Test
    fun `observe emits ProviderConfigurationChanged when SDK_UPDATE is fired`() = runTest(StandardTestDispatcher()) {
        val client = mockk<SplitClient>(relaxed = true)
        every { client.isReady } returns false
        val listeners = captureSplitListeners(client)

        stateRef.set(SplitProviderState(splitClient = client))
        val delegate = DefaultEventsDelegate(stateRef, registry)

        val job = async { withTimeout(2_000) { delegate.observe().take(1).toList().single() } }
        // Ensure the collector is started before firing the event
        runCurrent()

        // Trigger SDK_UPDATE
        listeners.updateTask.captured.onPostExecution(client)

        assertEquals(OpenFeatureProviderEvents.ProviderConfigurationChanged, job.await())
    }

    @Test
    fun `observe emits ProviderError when SDK_READY_TIMED_OUT is fired`() = runTest(StandardTestDispatcher()) {
        val client = mockk<SplitClient>(relaxed = true)
        every { client.isReady } returns false
        val listeners = captureSplitListeners(client)

        stateRef.set(SplitProviderState(splitClient = client))
        val delegate = DefaultEventsDelegate(stateRef, registry)

        val job = async { delegate.observe().first() }
        // Ensure the collector is started before firing the event
        runCurrent()

        // Trigger SDK_READY_TIMED_OUT
        listeners.timeoutTask.captured.onPostExecution(client)

        val event = job.await()
        require(event is OpenFeatureProviderEvents.ProviderError)
    }

    private data class CapturedTasks(
        val readyTask: CapturingSlot<SplitEventTask>,
        val updateTask: CapturingSlot<SplitEventTask>,
        val timeoutTask: CapturingSlot<SplitEventTask>,
    )

    private fun captureSplitListeners(client: SplitClient): CapturedTasks {
        val readySlot = slot<SplitEventTask>()
        val updateSlot = slot<SplitEventTask>()
        val timeoutSlot = slot<SplitEventTask>()

        every { client.on(SplitEvent.SDK_READY, capture(readySlot)) } answers { }
        every { client.on(SplitEvent.SDK_UPDATE, capture(updateSlot)) } answers { }
        every { client.on(SplitEvent.SDK_READY_TIMED_OUT, capture(timeoutSlot)) } answers { }

        // Registry registration triggers listener registration
        registry.register(client)

        return CapturedTasks(readySlot, updateSlot, timeoutSlot)
    }
}
