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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.TimeoutCancellationException

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
    fun `observe emits ProviderReady when SDK_READY_FROM_CACHE is fired`() =
        expectSingleEvent(
            prepare = { buildHarness(isClientReady = false) },
            trigger = { slots, client -> slots.readyTask.captured.onPostExecution(client) },
            expected = OpenFeatureProviderEvents.ProviderReady,
        )

    @Test
    fun `observe emits ProviderConfigurationChanged when SDK_UPDATE is fired`() =
        expectSingleEvent(
            prepare = { buildHarness(isClientReady = false) },
            trigger = { slots, client -> slots.updateTask.captured.onPostExecution(client) },
            expected = OpenFeatureProviderEvents.ProviderConfigurationChanged,
        )

    @Test
    fun `observe emits ProviderError when SDK_READY_TIMED_OUT is fired`() = runTest {
        val harness = buildHarness(isClientReady = false)
        val job = async { withTimeout(2_000) { harness.delegate.observe().first() } }
        runCurrent()
        harness.listeners.timeoutTask.captured.onPostExecution(harness.client)
        val event = job.await()
        require(event is OpenFeatureProviderEvents.ProviderError)
    }

    @Test
    fun `emits ProviderReady immediately when client is already ready`() = runTest {
        val harness = buildHarness(isClientReady = true)
        val event = withTimeout(2_000) { harness.delegate.observe().first() }
        assertEquals(OpenFeatureProviderEvents.ProviderReady, event)
    }

    private data class CapturedTasks(
        val readyTask: CapturingSlot<SplitEventTask>,
        val updateTask: CapturingSlot<SplitEventTask>,
        val timeoutTask: CapturingSlot<SplitEventTask>,
    )



    /** Small harness to prepare a delegate and capture its registered SDK listeners. */
    private data class Harness(
        val client: SplitClient,
        val delegate: DefaultEventsDelegate,
        val listeners: CapturedTasks,
    )

    private fun buildHarness(isClientReady: Boolean = false): Harness {
        val client = mockk<SplitClient>(relaxed = true)
        every { client.isReady } returns isClientReady
        val listeners = captureSplitListeners(client)
        stateRef.set(SplitProviderState(splitClient = client))
        val delegate = DefaultEventsDelegate(stateRef, registry)
        return Harness(client, delegate, listeners)
    }

    /** Expect a single event after triggering a specific SDK callback. */
    private fun expectSingleEvent(
        prepare: () -> Harness,
        trigger: (CapturedTasks, SplitClient) -> Unit,
        expected: OpenFeatureProviderEvents,
    ) = runTest {
        val harness = prepare()
        val job = async { withTimeout(2_000) { harness.delegate.observe().first() } }
        // Ensure collector started before triggering
        runCurrent()
        trigger(harness.listeners, harness.client)
        assertEquals(expected, job.await())
    }

    private fun captureSplitListeners(client: SplitClient): CapturedTasks {
        val readySlot = slot<SplitEventTask>()
        val updateSlot = slot<SplitEventTask>()
        val timeoutSlot = slot<SplitEventTask>()

        every { client.on(SplitEvent.SDK_READY_FROM_CACHE, capture(readySlot)) } answers { }
        every { client.on(SplitEvent.SDK_UPDATE, capture(updateSlot)) } answers { }
        every { client.on(SplitEvent.SDK_READY_TIMED_OUT, capture(timeoutSlot)) } answers { }

        registry.register(client)

        return CapturedTasks(readySlot, updateSlot, timeoutSlot)
    }
}
