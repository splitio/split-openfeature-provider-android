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
    fun `observe emits ProviderStale when SDK_READY_FROM_CACHE is fired`() =
        expectSingleEvent(
            prepare = { buildHarness(isClientReady = false) },
            trigger = { slots, client -> slots.readyFromCacheTask.captured.onPostExecution(client) },
            expected = OpenFeatureProviderEvents.ProviderStale,
        )

    @Test
    fun `observe emits ProviderReady when SDK_READY is fired`() =
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
    fun `observe emits ProviderError then ProviderReady on timeout followed by ready`() = runTest {
        val harness = buildHarness(isClientReady = false)
        val events = mutableListOf<OpenFeatureProviderEvents>()

        val job = async {
            harness.delegate.observe().collect { event ->
                events.add(event)
                if (events.size >= 2) return@collect
            }
        }
        runCurrent()

        // First, timeout occurs
        harness.listeners.timeoutTask.captured.onPostExecution(harness.client)
        runCurrent()

        // Then, client becomes ready
        harness.listeners.readyTask.captured.onPostExecution(harness.client)
        runCurrent()

        // Verify we got both events in sequence
        assertEquals(2, events.size)
        require(events[0] is OpenFeatureProviderEvents.ProviderError)
        assertEquals(OpenFeatureProviderEvents.ProviderReady, events[1])

        job.cancel()
    }

    @Test
    fun `emits ProviderReady immediately when client is already ready`() = runTest {
        val harness = buildHarness(isClientReady = true)

        // Start collecting events
        val job = async { withTimeout(2_000) { harness.delegate.observe().first() } }
        runCurrent()

        val event = job.await()
        // Should emit the first ready event from readyEvents (SDK_READY → ProviderReady)
        assertEquals(OpenFeatureProviderEvents.ProviderReady, event)
    }

    @Test
    fun `observe reflects new client after state switch`() = runTest {
        // Prepare client A
        val clientA = mockk<SplitClient>(relaxed = true)
        every { clientA.isReady } returns false
        val listenersA = captureSplitListeners(clientA)

        // Prepare client B (already ready to test immediate emission)
        val clientB = mockk<SplitClient>(relaxed = true)
        every { clientB.isReady } returns true
        val listenersB = captureSplitListeners(clientB)

        // Start with A and trigger ready event
        stateRef.set(SplitProviderState(splitClient = clientA))
        val delegateA = DefaultEventsDelegate(stateRef, registry)
        val jobA = async { withTimeout(2_000) { delegateA.observe().first() } }
        runCurrent()
        listenersA.readyTask.captured.onPostExecution(clientA)
        assertEquals(OpenFeatureProviderEvents.ProviderReady, jobA.await())

        // Switch to B and observe new delegate bound to B - should emit ProviderConfigurationChanged immediately
        stateRef.set(stateRef.get().copy(splitClient = clientB))
        val delegateB = DefaultEventsDelegate(stateRef, registry)
        val jobB = async { withTimeout(2_000) { delegateB.observe().first() } }
        runCurrent()
        assertEquals(OpenFeatureProviderEvents.ProviderConfigurationChanged, jobB.await())

        // Switch back to A - should still emit ProviderConfigurationChanged (not Ready)
        every { clientA.isReady } returns true  // Make clientA ready for immediate emission
        stateRef.set(stateRef.get().copy(splitClient = clientA))
        val delegateA2 = DefaultEventsDelegate(stateRef, registry)

        // For context change back to A, we need to use eventsForContextChange
        val jobA2 = async { withTimeout(2_000) { registry.eventsForContextChange(clientA).first() } }
        runCurrent()
        assertEquals(OpenFeatureProviderEvents.ProviderConfigurationChanged, jobA2.await())
    }

    @Test
    fun `observe returns empty flow when splitClient is null`() = runTest {
        // Set state with null splitClient
        stateRef.set(SplitProviderState(splitClient = null))
        val delegate = DefaultEventsDelegate(stateRef, registry)

        // Should return empty flow and not crash
        val job = async {
            try {
                withTimeout(1_000) {
                    delegate.observe().first()
                }
                error("Expected exception - empty flow should not emit")
            } catch (e: NoSuchElementException) {
                // Expected - empty flow throws NoSuchElementException when calling first()
                "success"
            } catch (e: TimeoutCancellationException) {
                // Also acceptable - timeout if flow doesn't emit
                "success"
            }
        }
        runCurrent()

        val result = job.await()
        assertEquals("success", result)
    }

    @Test
    fun `client becomes ready between isReady check and listener registration`() = runTest {
        val client = mockk<SplitClient>(relaxed = true)
        val readySlot = slot<SplitEventTask>()
        val updateSlot = slot<SplitEventTask>()
        val timeoutSlot = slot<SplitEventTask>()

        // Initially not ready
        every { client.isReady } returns false

        // Capture event listeners when they are registered
        every { client.on(SplitEvent.SDK_READY, capture(readySlot)) } answers {
            // Simulate client becoming ready right after listener registration
            every { client.isReady } returns true
        }
        every { client.on(SplitEvent.SDK_READY_FROM_CACHE, capture(slot())) } answers { }
        every { client.on(SplitEvent.SDK_UPDATE, capture(updateSlot)) } answers { }
        every { client.on(SplitEvent.SDK_READY_TIMED_OUT, capture(timeoutSlot)) } answers { }

        val bridge = registry.register(client)

        val job = async { withTimeout(2_000) { bridge.events.first() } }
        runCurrent()

        readySlot.captured.onPostExecution(client)

        val event = job.await()
        assertEquals(OpenFeatureProviderEvents.ProviderReady, event)
    }

    private data class CapturedTasks(
        val readyFromCacheTask: CapturingSlot<SplitEventTask>,
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
        val readyFromCacheSlot = slot<SplitEventTask>()
        val readySlot = slot<SplitEventTask>()
        val updateSlot = slot<SplitEventTask>()
        val timeoutSlot = slot<SplitEventTask>()

        every { client.on(SplitEvent.SDK_READY_FROM_CACHE, capture(readyFromCacheSlot)) } answers { }
        every { client.on(SplitEvent.SDK_READY, capture(readySlot)) } answers { }
        every { client.on(SplitEvent.SDK_UPDATE, capture(updateSlot)) } answers { }
        every { client.on(SplitEvent.SDK_READY_TIMED_OUT, capture(timeoutSlot)) } answers { }

        registry.register(client)

        return CapturedTasks(readyFromCacheSlot, readySlot, updateSlot, timeoutSlot)
    }
}
