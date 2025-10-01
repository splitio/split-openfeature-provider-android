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
    mapping: EventsMapping,
    private val isContextChange: Boolean = false,
    private val registry: SplitEventsRegistry? = null,
) {
    private val _events = MutableSharedFlow<OpenFeatureProviderEvents>(
        // replay=1 ensures late subscribers receive the most recent event (e.g., ProviderReady)
        // even if they start observing after the client is already ready. Without this, late
        // observers would never know the provider's current state.
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<OpenFeatureProviderEvents> = _events

    init {
        // Track if ProviderReady was emitted during listener registration to avoid double emission
        var readyEmittedDuringRegistration = false

        mapping.splitToProvider.forEach { (splitEvent: SplitEvent, providerEventFactory: () -> OpenFeatureProviderEvents) ->
            val task = object : SplitEventTask() {
                override fun onPostExecution(splitClient: SplitClient?) {
                    val event = providerEventFactory()
                    _events.tryEmit(event)
                    // Mark that we've emitted ProviderReady
                    if (event == OpenFeatureProviderEvents.ProviderReady) {
                        readyEmittedDuringRegistration = true
                        registry?.markProviderReadyEmitted()
                    }
                }
            }
            client.on(splitEvent, task)
        }

        runCatching {
            if (client.isReady) {
                if (isContextChange) {
                    // Context changes always emit ProviderConfigurationChanged immediately
                    _events.tryEmit(OpenFeatureProviderEvents.ProviderConfigurationChanged)
                } else if (!readyEmittedDuringRegistration) {
                    // Only emit ProviderReady if it wasn't already emitted during listener registration
                    _events.tryEmit(OpenFeatureProviderEvents.ProviderReady)
                    registry?.markProviderReadyEmitted()
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
    private var hasEmittedProviderReady = false
    private var currentClient: SplitClient? = null

    fun register(client: SplitClient): SplitEventsBridge =
        bridges.getOrPutConcurrent(client) {
            val isContextChange = hasEmittedProviderReady
            SplitEventsBridge(client, mapping, isContextChange, this)
        }

    fun markProviderReadyEmitted() {
        hasEmittedProviderReady = true
    }

    fun events(client: SplitClient): Flow<OpenFeatureProviderEvents> {
        // If switching to a different client after emitting ProviderReady,
        // clear the existing bridge to force creation of a context-change bridge
        val notFirstClient = currentClient != null
        if (notFirstClient && currentClient != client && hasEmittedProviderReady) {
            bridges.remove(client)
        }
        currentClient = client
        return register(client).events
    }
}
