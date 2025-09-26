package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.InvalidContextError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ProviderNotReadyError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.TargetingKeyMissingError
import io.split.android.client.SplitClient
import io.split.openfeature.android.provider.EvaluationContextExt.getTrafficType
import java.util.concurrent.atomic.AtomicReference

internal interface TrackingDelegate {

    fun track(
        trackingEventName: String,
        context: EvaluationContext?,
        details: TrackingEventDetails?
    )
}

internal class DefaultTrackingDelegate(
    private val state: AtomicReference<SplitProviderState>,
) : TrackingDelegate {

    override fun track(
        trackingEventName: String,
        context: EvaluationContext?,
        details: TrackingEventDetails?
    ) {
        val (trafficType, client) = getTrafficTypeAndClient(context)
        val value: Double? = details?.value?.toDouble()
        val properties: Map<String, Any?>? = details?.asObjectMap()
        if (value != null) {
            client.track(trafficType, trackingEventName, value, properties)
        } else {
            client.track(trafficType, trackingEventName, properties)
        }
    }

    private fun getTrafficTypeAndClient(context: EvaluationContext?): Pair<String?, SplitClient> {
        // Get current state
        val currentState = state.get()

        // Define evaluation context
        val evalContext = context ?: currentState.defaultContext
        ?: throw TargetingKeyMissingError("Targeting key missing in evaluation context")

        val requestedKey = evalContext.getTargetingKey()

        if (requestedKey.isBlank()) {
            throw TargetingKeyMissingError("Targeting key missing in evaluation context")
        }

        if (evalContext.getTrafficType().isNullOrEmpty()) {
            throw InvalidContextError("Missing trafficType, required to track. Set it in context with EvaluationContext.withTrafficType")
        }

        val client = currentState.splitClient ?: throw ProviderNotReadyError()

        // Ensure the active client matches the requested key. To use a different key, caller must setContext first.
        if (currentState.activeKey != requestedKey) {
            throw ProviderNotReadyError("Requested targetingKey ('$requestedKey') differs from active key ('${currentState.activeKey}'). Call setContext first to switch.")
        }

        return Pair(evalContext.getTrafficType(), client)
    }
}
