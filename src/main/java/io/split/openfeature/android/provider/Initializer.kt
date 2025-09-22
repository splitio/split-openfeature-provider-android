package io.split.openfeature.android.provider

import android.content.Context
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

interface FeatureProviderInitializer {
    @Throws(OpenFeatureError::class, CancellationException::class)
    suspend fun initialize(initialContext: EvaluationContext?)

    @Throws(OpenFeatureError::class, CancellationException::class)
    suspend fun onContextSet(oldContext: EvaluationContext?, newContext: EvaluationContext)
    fun shutdown()
}

/**
 * Handles initialization, context changes and shutdown for SplitProvider.
 */
internal class Initializer(
    private val stateRef: AtomicReference<SplitProviderState>,
    private val config: SplitProvider.Config,
    private val sdkManager: SdkManager,
    private val defaultReadyTimeoutMs: Long,
) : FeatureProviderInitializer {
    private val initMutex = Mutex()

    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun initialize(initialContext: EvaluationContext?) {
        if (stateRef.get().initialized) {
            return
        }

        initMutex.withLock {
            val current = stateRef.get()
            if (current.initialized) {
                return
            }

            val ctxToStore = current.defaultContext ?: initialContext
            val targetingKey = requireTargetingKey(ctxToStore)

            val (factory, client) = initializeSdkOrThrow(
                appContext = config.applicationContext,
                sdkKey = config.sdkKey,
                targetingKey = targetingKey
            )

            stateRef.set(
                SplitProviderState(
                    initialized = true,
                    defaultContext = ctxToStore,
                    splitFactory = factory,
                    splitClient = client,
                    activeKey = targetingKey,
                    clients = mapOf(targetingKey to client)
                )
            )
        }
    }

    @Throws(OpenFeatureError::class, CancellationException::class)
    override suspend fun onContextSet(
        oldContext: EvaluationContext?,
        newContext: EvaluationContext
    ) {
        if (!stateRef.get().initialized) {
            return
        }

        initMutex.withLock {
            val current = stateRef.get()
            if (!current.initialized) {
                return
            }

            if (newContext == oldContext) {
                return
            }

            val newKey: String = newContext.getTargetingKey()
            val oldKey = current.defaultContext?.getTargetingKey()

            if (newKey.isBlank() || newKey == oldKey) {
                stateRef.set(current.copy(defaultContext = newContext))
                return
            }

            val currentFactory = current.splitFactory
                ?: throw OpenFeatureError.ProviderFatalError()

            // Reuse cached client if available
            val cached = current.clients[newKey]
            if (cached != null) {
                stateRef.set(
                    current.copy(
                        splitClient = cached,
                        defaultContext = newContext,
                        activeKey = newKey
                    )
                )
                return
            }

            // Otherwise, create and cache a new ready client for this key
            val newClient = getReadyClientOrThrow(
                factory = currentFactory,
                targetingKey = newKey
            )

            stateRef.set(
                current.copy(
                    splitClient = newClient,
                    defaultContext = newContext,
                    activeKey = newKey,
                    clients = current.clients + (newKey to newClient)
                )
            )
        }
    }

    override fun shutdown() {
        // For now, no-op. Will implement later.
    }

    private fun requireTargetingKey(ctx: EvaluationContext?): String {
        return ctx?.getTargetingKey() ?: throw OpenFeatureError.ProviderFatalError()
    }

    private suspend fun initializeSdkOrThrow(
        appContext: Context,
        sdkKey: String,
        targetingKey: String,
    ): Pair<SplitFactory, SplitClient> {
        return mapSdkInitializerExceptions {
            sdkManager.initialize(
                appContext = appContext,
                sdkKey = sdkKey,
                targetingKey = targetingKey,
                timeoutMs = defaultReadyTimeoutMs
            )
        }
    }

    private suspend fun getReadyClientOrThrow(
        factory: SplitFactory,
        targetingKey: String,
    ): SplitClient {
        return mapSdkInitializerExceptions {
            sdkManager.getReadyClient(
                factory = factory,
                targetingKey = targetingKey,
                timeoutMs = defaultReadyTimeoutMs
            )
        }
    }

    private suspend fun <T> mapSdkInitializerExceptions(block: suspend () -> T): T {
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: IllegalStateException) {
            throw OpenFeatureError.ProviderNotReadyError()
        } catch (t: Throwable) {
            throw OpenFeatureError.ProviderFatalError()
        }
    }
}
