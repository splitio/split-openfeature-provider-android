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
internal class SplitEventsBridge(
    private val client: SplitClient,
    private val mapping: EventsMapping,
) {
    private val _events = MutableSharedFlow<OpenFeatureProviderEvents>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<OpenFeatureProviderEvents> = _events

    init {
        // Eagerly emit readiness if the client is already ready
        runCatching {
            val firstReady = mapping.readyEvents.firstOrNull()
            val eventFactory = firstReady?.let { mapping.splitToProvider[it] }
            if (client.isReady && eventFactory != null) {
                _events.tryEmit(eventFactory())
            }
        }

        // Register a listener per mapped SplitEvent
        mapping.splitToProvider.forEach { (splitEvent: SplitEvent, providerEventFactory: () -> OpenFeatureProviderEvents) ->
            val task = object : SplitEventTask() {
                override fun onPostExecution(splitClient: SplitClient?) {
                    _events.tryEmit(providerEventFactory())
                }
            }
            client.on(splitEvent, task)
        }
    }
}

/**
 * Registry to ensure a single SplitEventsBridge per SplitClient instance.
 */
internal class SplitEventsRegistry(
    private val mapping: EventsMapping = DefaultEventsMapping
) {
    private val bridges = ConcurrentHashMap<SplitClient, SplitEventsBridge>()

    fun register(client: SplitClient): SplitEventsBridge =
        bridges.getOrPutConcurrent(client) { SplitEventsBridge(client, mapping) }

    fun events(client: SplitClient): Flow<OpenFeatureProviderEvents> =
        register(client).events
}
