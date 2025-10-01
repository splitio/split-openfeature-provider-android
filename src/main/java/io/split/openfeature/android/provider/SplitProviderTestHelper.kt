package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.ProviderMetadata
import io.split.android.client.SplitFactory
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

/**
 * Helper function to create a SplitProvider with an injected SplitFactory for testing.
 * This ensures the state reference is properly shared between the provider and initializer.
 */
@VisibleForTesting
fun createTestSplitProvider(
    splitFactory: SplitFactory,
    config: SplitProvider.Config
): SplitProvider {
    val sharedState = AtomicReference(SplitProviderState())
    val sharedEventsRegistry = SplitEventsRegistry()
    
    return SplitProvider(
        hooks = emptyList(),
        metadata = object : ProviderMetadata {
            override val name = "Split"
        },
        config = config,
        state = sharedState,
        dispatcher = kotlinx.coroutines.Dispatchers.IO,
        eventsRegistry = sharedEventsRegistry,
        initializer = DefaultInitializerDelegate(
            stateRef = sharedState,
            config = config,
            sdkManager = SplitSdkDelegate(
                dispatcher = kotlinx.coroutines.Dispatchers.IO,
                eventsRegistry = sharedEventsRegistry,
                injectedFactory = splitFactory
            ),
            defaultReadyTimeoutMs = 10_000L
        ),
        evaluatorDelegate = DefaultEvaluator(sharedState),
        trackingDelegate = DefaultTrackingDelegate(sharedState),
        eventsDelegate = DefaultEventsDelegate(sharedState, sharedEventsRegistry)
    )
}
