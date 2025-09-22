package io.split.openfeature.android.provider

import android.content.Context
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

class SplitProvider internal constructor(
    override val hooks: List<Hook<*>> = emptyList(),
    override val metadata: ProviderMetadata = object : ProviderMetadata {
        override val name = NAME
    },
    private val config: Config,
    private val state: AtomicReference<SplitProviderState> = AtomicReference(SplitProviderState()),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val initializer: Initializer = Initializer(
        stateRef = state,
        config = config,
        sdkManager = SplitSdkManager(dispatcher),
        defaultReadyTimeoutMs = DEFAULT_READY_TIMEOUT_MS
    ),
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

    // Delegate lifecycle to initializer collaborator

    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun initialize(initialContext: EvaluationContext?) {
        initializer.initialize(initialContext)
    }

    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun onContextSet(
        oldContext: EvaluationContext?, newContext: EvaluationContext
    ) {
        initializer.onContextSet(oldContext, newContext)
    }

    override fun getBooleanEvaluation(
        key: String, defaultValue: Boolean, context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        TODO("Not yet implemented")
    }

    override fun getDoubleEvaluation(
        key: String, defaultValue: Double, context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        TODO("Not yet implemented")
    }

    override fun getIntegerEvaluation(
        key: String, defaultValue: Int, context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        TODO("Not yet implemented")
    }

    override fun getObjectEvaluation(
        key: String, defaultValue: Value, context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        TODO("Not yet implemented")
    }

    override fun getStringEvaluation(
        key: String, defaultValue: String, context: EvaluationContext?
    ): ProviderEvaluation<String> {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        initializer.shutdown()
    }

    /**
     * Configuration holder for the provider.
     */
    data class Config(
        val applicationContext: Context,
        val sdkKey: String,
    )

    private companion object {
        const val NAME = "Split"
        const val DEFAULT_READY_TIMEOUT_MS = 10_000L
    }
}
