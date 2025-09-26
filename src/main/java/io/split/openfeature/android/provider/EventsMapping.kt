package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.split.android.client.events.SplitEvent

/**
 * Defines how Split SDK events map to OpenFeature provider events, and which events indicate readiness.
 */
internal data class EventsMapping(
    val splitToProvider: Map<SplitEvent, () -> OpenFeatureProviderEvents>,
    val readyEvents: Set<SplitEvent>,
)

internal val DefaultEventsMapping: EventsMapping = EventsMapping(
    splitToProvider = mapOf(
        SplitEvent.SDK_READY_FROM_CACHE to { OpenFeatureProviderEvents.ProviderReady },
        SplitEvent.SDK_UPDATE to { OpenFeatureProviderEvents.ProviderConfigurationChanged },
        SplitEvent.SDK_READY_TIMED_OUT to { OpenFeatureProviderEvents.ProviderError(OpenFeatureError.ProviderNotReadyError()) },
    ),
    readyEvents = setOf(SplitEvent.SDK_READY_FROM_CACHE)
)
