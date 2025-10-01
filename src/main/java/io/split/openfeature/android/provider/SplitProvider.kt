package io.split.openfeature.android.provider

import android.content.Context
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class SplitProvider internal constructor(
    override val hooks: List<Hook<*>> = emptyList(),
    override val metadata: ProviderMetadata = object : ProviderMetadata {
        override val name = NAME
    },
    private val config: Config,
    private val state: AtomicReference<SplitProviderState> = AtomicReference(SplitProviderState()),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val eventsRegistry: SplitEventsRegistry = SplitEventsRegistry(),
    private val initializer: InitializerDelegate = DefaultInitializerDelegate(
        stateRef = state,
        config = config,
        sdkManager = SplitSdkDelegate(dispatcher, eventsRegistry),
        defaultReadyTimeoutMs = DEFAULT_READY_TIMEOUT_MS
    ),
    private val evaluatorDelegate: EvaluatorDelegate = DefaultEvaluator(state),
    private val trackingDelegate: TrackingDelegate = DefaultTrackingDelegate(state),
    private val eventsDelegate: EventsDelegate = DefaultEventsDelegate(state, eventsRegistry),
) : FeatureProvider {

    constructor(
        hooks: List<Hook<*>> = emptyList(),
        metadata: ProviderMetadata = object : ProviderMetadata {
            override val name = NAME
        },
        config: Config,
    ) : this(
        hooks = hooks,
        metadata = metadata,
        config = config,
        state = AtomicReference(SplitProviderState()),
        dispatcher = Dispatchers.IO
    )


    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun initialize(initialContext: EvaluationContext?) =
        initializer.initialize(initialContext)

    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun onContextSet(
        oldContext: EvaluationContext?, newContext: EvaluationContext
    ) = initializer.onContextSet(oldContext, newContext)

    override fun getBooleanEvaluation(
        key: String, defaultValue: Boolean, context: EvaluationContext?
    ): ProviderEvaluation<Boolean> =
        evaluatorDelegate.getBooleanEvaluation(key, defaultValue, context)

    override fun getDoubleEvaluation(
        key: String, defaultValue: Double, context: EvaluationContext?
    ): ProviderEvaluation<Double> =
        evaluatorDelegate.getDoubleEvaluation(key, defaultValue, context)

    override fun getIntegerEvaluation(
        key: String, defaultValue: Int, context: EvaluationContext?
    ): ProviderEvaluation<Int> = evaluatorDelegate.getIntegerEvaluation(key, defaultValue, context)

    override fun getObjectEvaluation(
        key: String, defaultValue: Value, context: EvaluationContext?
    ): ProviderEvaluation<Value> = evaluatorDelegate.getObjectEvaluation(key, defaultValue, context)

    override fun getStringEvaluation(
        key: String, defaultValue: String, context: EvaluationContext?
    ): ProviderEvaluation<String> =
        evaluatorDelegate.getStringEvaluation(key, defaultValue, context)

    override fun shutdown() = initializer.shutdown()

    override fun track(
        trackingEventName: String,
        context: EvaluationContext?,
        details: TrackingEventDetails?
    ) {
        trackingDelegate.track(trackingEventName, context, details)
    }

    override fun observe(): Flow<OpenFeatureProviderEvents> {
        return eventsDelegate.observe()
    }

    /**
     * Configuration holder for the provider.
     **/
    data class Config(
        val applicationContext: Context,
        val sdkKey: String,
    )

    private companion object {
        const val NAME = "Split"
        const val DEFAULT_READY_TIMEOUT_MS = 10_000L
    }
}
