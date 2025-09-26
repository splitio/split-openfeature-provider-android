package io.split.openfeature.android.provider

import android.content.Context
import io.split.android.client.SplitClient
import io.split.android.client.SplitClientConfig
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Abstracts the initialization of the Split SDK.
 */
internal interface SdkDelegate {
    /**
     * Build a SplitFactory and obtain a SplitClient for the provided targetingKey, awaiting readiness.
     * Returns Pair<SplitFactory, SplitClient> when READY. Throws on timeout or failures.
     */
    suspend fun initialize(
        appContext: Context,
        sdkKey: String,
        targetingKey: String,
        timeoutMs: Long
    ): Pair<SplitFactory, SplitClient>

    /**
     * Retrieve a SplitClient for the given key from a SplitFactory and await readiness.
     */
    suspend fun getReadyClient(factory: SplitFactory, targetingKey: String, timeoutMs: Long): SplitClient
}

internal class SplitSdkDelegate(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val eventsRegistry: SplitEventsRegistry,
    private val eventsMapping: EventsMapping = DefaultEventsMapping,
) : SdkDelegate {
    override suspend fun initialize(
        appContext: Context,
        sdkKey: String,
        targetingKey: String,
        timeoutMs: Long
    ): Pair<SplitFactory, SplitClient> {
        val factory = withContext(dispatcher) {
            SplitFactoryBuilder.build(
                sdkKey,
                Key(targetingKey),
                SplitClientConfig.builder().build(),
                appContext
            )
        }

        val client = getReadyClient(factory, targetingKey, timeoutMs)
        return factory to client
    }

    override suspend fun getReadyClient(
        factory: SplitFactory,
        targetingKey: String,
        timeoutMs: Long
    ): SplitClient {
        val client: SplitClient = factory.client(Key(targetingKey))
        val ready = CompletableDeferred<Unit>()

        eventsMapping.readyEvents.forEach { event: SplitEvent ->
            client.on(event, object : SplitEventTask() {
                override fun onPostExecution(client: SplitClient?) {
                    if (!ready.isCompleted) {
                        ready.complete(Unit)
                    }
                }
            })
        }

        runCatching {
            if (client.isReady && !ready.isCompleted) {
                ready.complete(Unit)
            }
        }

        withTimeout(timeoutMs) { ready.await() }

        // Register the long-lived events bridge once per client
        eventsRegistry.register(client)

        return client
    }
}
