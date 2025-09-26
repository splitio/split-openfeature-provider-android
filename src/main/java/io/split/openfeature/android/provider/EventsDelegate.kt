package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.split.android.client.SplitClient
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal interface EventsDelegate {

    fun observe(): Flow<OpenFeatureProviderEvents>
}

internal class DefaultEventsDelegate(
    state: AtomicReference<SplitProviderState>,
    private val eventsRegistry: SplitEventsRegistry,
) : EventsDelegate {

    val splitClient: SplitClient? = state.get().splitClient

    override fun observe(): Flow<OpenFeatureProviderEvents> {
        return splitClient?.let { eventsRegistry.events(it) } ?: kotlinx.coroutines.flow.emptyFlow()
    }
}

/**
 * Bridge that registers SDK listeners once per SplitClient and exposes them as a hot Flow.
 */
internal class SplitEventsBridge(private val client: SplitClient) {
    private val _events = MutableSharedFlow<OpenFeatureProviderEvents>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<OpenFeatureProviderEvents> = _events

    init {
        // Emit current readiness if available
        runCatching { if (client.isReady) _events.tryEmit(OpenFeatureProviderEvents.ProviderReady) }

        val readyTask = object : SplitEventTask() {
            override fun onPostExecution(splitClient: SplitClient?) {
                _events.tryEmit(OpenFeatureProviderEvents.ProviderReady)
            }
        }

        val updateTask = object : SplitEventTask() {
            override fun onPostExecution(splitClient: SplitClient?) {
                _events.tryEmit(OpenFeatureProviderEvents.ProviderConfigurationChanged)
            }
        }

        val timeoutTask = object : SplitEventTask() {
            override fun onPostExecution(splitClient: SplitClient?) {
                _events.tryEmit(OpenFeatureProviderEvents.ProviderError(OpenFeatureError.ProviderNotReadyError()))
            }
        }

        client.on(SplitEvent.SDK_READY, readyTask)
        client.on(SplitEvent.SDK_UPDATE, updateTask)
        client.on(SplitEvent.SDK_READY_TIMED_OUT, timeoutTask)
    }
}

/**
 * Registry to ensure a single SplitEventsBridge per SplitClient instance.
 */
internal class SplitEventsRegistry {
    private val bridges = ConcurrentHashMap<SplitClient, SplitEventsBridge>()

    fun register(client: SplitClient): SplitEventsBridge =
        bridges.getOrPutConcurrent(client) { SplitEventsBridge(client) }

    fun events(client: SplitClient): Flow<OpenFeatureProviderEvents> =
        register(client).events
}
