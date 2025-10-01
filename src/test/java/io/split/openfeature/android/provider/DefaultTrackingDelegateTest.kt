package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.InvalidContextError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ProviderNotReadyError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.TargetingKeyMissingError
import io.mockk.mockk
import io.mockk.verify
import io.split.android.client.SplitClient
import io.split.openfeature.android.provider.EvaluationContextExt.withTrafficType
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFailsWith

class DefaultTrackingDelegateTest {

    private fun state(
        context: EvaluationContext? = ImmutableContext("user-123").withTrafficType("user"),
        client: SplitClient? = mockk(relaxed = true),
        activeKey: String? = "user-123",
    ): AtomicReference<SplitProviderState> = AtomicReference(
        SplitProviderState(
            initialized = true,
            defaultContext = context,
            splitFactory = null,
            splitClient = client,
            activeKey = activeKey
        )
    )

    @Test
    fun `tracks with numeric value and properties`() {
        val client = mockk<SplitClient>(relaxed = true)
        val state = state(client = client)
        val delegate = DefaultTrackingDelegate(state)

        val details = TrackingEventDetails(
            value = 12.0,
            structure = ImmutableStructure(mapOf(
                "p1" to Value.String("v1"),
                "p2" to Value.Integer(2)
            ))
        )

        delegate.track("purchase", null, details)

        verify(exactly = 1) { client.track("user", "purchase", 12.0, mapOf("p1" to "v1", "p2" to 2)) }
        verify(exactly = 0) { client.track("user", "purchase", any<Map<String, Any?>>()) }
    }

    @Test
    fun `tracks without value uses properties-only overload`() {
        val client = mockk<SplitClient>(relaxed = true)
        val state = state(client = client)
        val delegate = DefaultTrackingDelegate(state)

        val details = TrackingEventDetails(
            value = null,
            structure = ImmutableStructure(mapOf(
                "a" to Value.Boolean(true)
            ))
        )

        delegate.track("view", null, details)

        verify(exactly = 1) { client.track("user", "view", mapOf("a" to true)) }
        verify(exactly = 0) { client.track("user", "view", any<Double>(), any()) }
    }

    @Test
    fun `throws when context missing and no default context`() {
        val state = state(context = null)
        val delegate = DefaultTrackingDelegate(state)

        assertFailsWith<TargetingKeyMissingError> {
            delegate.track("event", null, null)
        }
    }

    @Test
    fun `uses default user traffic type when trafficType is missing`() {
        val client = mockk<SplitClient>(relaxed = true)
        val ctx = ImmutableContext(targetingKey = "user-123")
        val state = state(context = ctx, client = client)
        val delegate = DefaultTrackingDelegate(state)

        delegate.track("event", ctx, null)

        verify(exactly = 1) { client.track("user", "event", null as Map<String, Any?>?) }
    }

    @Test
    fun `throws when split client is not ready`() {
        val ctx = ImmutableContext("user-123").withTrafficType("user")
        val state = state(context = ctx, client = null)
        val delegate = DefaultTrackingDelegate(state)

        assertFailsWith<ProviderNotReadyError> {
            delegate.track("event", ctx, null)
        }
    }

    @Test
    fun `throws when trying to track with a key different than the active key`() {
        val client = mockk<SplitClient>(relaxed = true)
        val ctx = ImmutableContext("new-user").withTrafficType("user")
        val state = state(context = ctx, client = client, activeKey = "user-123")
        val delegate = DefaultTrackingDelegate(state)

        assertFailsWith<ProviderNotReadyError> {
            delegate.track("event", ctx, null)
        }
    }

    @Test
    fun `uses default context traffic type when passed context has none`() {
        val client = mockk<SplitClient>(relaxed = true)
        val defaultCtx = ImmutableContext("user-123").withTrafficType("user")
        val passedCtx = ImmutableContext("user-123")
        val state = state(context = defaultCtx, client = client)
        val delegate = DefaultTrackingDelegate(state)

        delegate.track("event", passedCtx, null)

        verify(exactly = 1) { client.track("user", "event", null as Map<String, Any?>?) }
    }

    @Test
    fun `uses custom traffic type when explicitly set in passed context`() {
        val client = mockk<SplitClient>(relaxed = true)
        val defaultCtx = ImmutableContext("user-123").withTrafficType("user")
        val passedCtx = ImmutableContext("user-123").withTrafficType("account")
        val state = state(context = defaultCtx, client = client)
        val delegate = DefaultTrackingDelegate(state)

        delegate.track("event", passedCtx, null)

        verify(exactly = 1) { client.track("account", "event", null as Map<String, Any?>?) }
    }

    @Test
    fun `uses custom traffic type when set in default context`() {
        val client = mockk<SplitClient>(relaxed = true)
        val defaultCtx = ImmutableContext("user-123").withTrafficType("organization")
        val state = state(context = defaultCtx, client = client)
        val delegate = DefaultTrackingDelegate(state)

        delegate.track("event", null, null)

        verify(exactly = 1) { client.track("organization", "event", null as Map<String, Any?>?) }
    }

    @Test
    fun `uses default user traffic type when neither passed nor default context has traffic type`() {
        val client = mockk<SplitClient>(relaxed = true)
        val defaultCtx = ImmutableContext("user-123") // no traffic type
        val passedCtx = ImmutableContext("user-123") // no traffic type
        val state = state(context = defaultCtx, client = client)
        val delegate = DefaultTrackingDelegate(state)

        delegate.track("event", passedCtx, null)

        verify(exactly = 1) { client.track(Constants.DEFAULT_TRAFFIC_TYPE, "event", null as Map<String, Any?>?) }
    }
}
