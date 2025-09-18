package io.split.openfeature.android.provider

import android.content.Context
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

class SplitProvider internal constructor(
    override val hooks: List<Hook<*>> = emptyList(),
    override val metadata: ProviderMetadata = object : ProviderMetadata {
        override val name = NAME
    },
    private val config: Config,
    private val sdkInitializer: SdkInitializer,
) : FeatureProvider {

    // Public convenience constructor
    constructor(
        hooks: List<Hook<*>> = emptyList(),
        metadata: ProviderMetadata = object : ProviderMetadata {
            override val name = NAME
        },
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        config: Config,
    ) : this(
        hooks = hooks,
        metadata = metadata,
        config = config,
        sdkInitializer = SplitSdkInitializer(dispatcher)
    )


    private val state: AtomicReference<State> =
        AtomicReference(State())

    private val initMutex = Mutex()

    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun initialize(initialContext: EvaluationContext?) {
        if (state.get().initialized) {
            return
        }

        initMutex.withLock {
            val current = state.get()
            if (current.initialized) {
                return
            }

            val ctxToStore = current.defaultContext ?: initialContext
            if (ctxToStore?.getTargetingKey() == null) {
                throw OpenFeatureError.ProviderFatalError()
            }
            val targetingKey = ctxToStore.getTargetingKey()

            val factoryAndClient = try {
                sdkInitializer.initialize(
                    appContext = config.applicationContext,
                    sdkKey = config.sdkKey,
                    targetingKey = targetingKey,
                    timeoutMs = DEFAULT_READY_TIMEOUT_MS
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: IllegalStateException) {
                throw OpenFeatureError.ProviderNotReadyError()
            } catch (t: Throwable) {
                throw OpenFeatureError.ProviderFatalError()
            }

            val (factory, client) = factoryAndClient
            state.set(
                State(
                    initialized = true,
                    defaultContext = ctxToStore,
                    splitFactory = factory,
                    splitClient = client
                )
            )
        }
    }

    override suspend fun onContextSet(
        oldContext: EvaluationContext?, newContext: EvaluationContext
    ) {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    /**
     * Configuration holder for the provider.
     */
    data class Config(
        val applicationContext: Context,
        val sdkKey: String,
    )

    /**
     * Internal state holder
     */
    private data class State(
        val initialized: Boolean = false,
        val defaultContext: EvaluationContext? = null,
        val splitFactory: SplitFactory? = null,
        val splitClient: SplitClient? = null,
    )
    private companion object {
        const val NAME = "Split"
        const val DEFAULT_READY_TIMEOUT_MS = 10_000L
    }
}
