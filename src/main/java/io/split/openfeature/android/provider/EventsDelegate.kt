package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import io.split.android.client.SplitClient
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal interface EventsDelegate {

    fun observe(): Flow<OpenFeatureProviderEvents>
}

internal class DefaultEventsDelegate(
    private val state: AtomicReference<SplitProviderState>,
    private val eventsRegistry: SplitEventsRegistry,
) : EventsDelegate {

    override fun observe(): Flow<OpenFeatureProviderEvents> {
        val splitClient = state.get().splitClient
        return splitClient?.let { eventsRegistry.events(it) } ?: emptyFlow()
    }
}

/**
 * Bridge that registers SDK listeners once per SplitClient and exposes them as a hot Flow.
 */
internal class SplitEventsBridge(
    private val client: SplitClient,
    private val mapping: EventsMapping,
    private val isContextChange: Boolean = false,
) {
    private val _events = MutableSharedFlow<OpenFeatureProviderEvents>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<OpenFeatureProviderEvents> = _events

    init {
        mapping.splitToProvider.forEach { (splitEvent: SplitEvent, providerEventFactory: () -> OpenFeatureProviderEvents) ->
            val task = object : SplitEventTask() {
                override fun onPostExecution(splitClient: SplitClient?) {
                    _events.tryEmit(providerEventFactory())
                }
            }
            client.on(splitEvent, task)
        }

        runCatching {
            if (client.isReady) {
                if (isContextChange) {
                    _events.tryEmit(OpenFeatureProviderEvents.ProviderConfigurationChanged)
                } else {
                    val firstReady = mapping.readyEvents.firstOrNull()
                    val eventFactory = firstReady?.let { mapping.splitToProvider[it] }
                    if (eventFactory != null) {
                        _events.tryEmit(eventFactory())
                    }
                }
            }
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
    private var hasInitialClient = false

    fun register(client: SplitClient): SplitEventsBridge =
        bridges.getOrPutConcurrent(client) {
            val isContextChange = hasInitialClient
            hasInitialClient = true
            SplitEventsBridge(client, mapping, isContextChange)
        }

    fun events(client: SplitClient): Flow<OpenFeatureProviderEvents> =
        register(client).events
}
