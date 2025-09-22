package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.mockk.every
import io.mockk.mockk
import io.split.android.client.SplitClient
import io.split.android.client.SplitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EvaluatorTest : BaseMockkTest() {

    private fun evaluatorWith(
        client: SplitClient? = null,
        defaultContext: EvaluationContext? = null,
        clients: Map<String, SplitClient>? = null
    ): DefaultEvaluator {
        val targetingKey = defaultContext?.getTargetingKey()
        val clientsMap = when {
            clients != null -> clients
            client != null && !targetingKey.isNullOrBlank() -> mapOf(targetingKey to client)
            else -> emptyMap()
        }
        val state = AtomicReference(
            SplitProviderState(
                initialized = client != null,
                defaultContext = defaultContext,
                splitFactory = null,
                splitClient = client,
                activeKey = targetingKey,
                clients = clientsMap
            )
        )
        return DefaultEvaluator(state)
    }

    @Test(expected = OpenFeatureError.ProviderNotReadyError::class)
    fun `throws ProviderNotReady when client is null`() {
        val evaluator = evaluatorWith(client = null)
        evaluator.getBooleanEvaluation("flag", defaultValue = true, context = ImmutableContext(targetingKey = "user"))
    }

    @Test
    fun `maps on and true to boolean true`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returnsMany listOf(
            SplitResult("on", null),
            SplitResult("true", null)
        )

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        // Call twice to consume returnsMany
        assertTrue(evaluator.getBooleanEvaluation("flag", defaultValue = false, context = ctx).value)
        assertTrue(evaluator.getBooleanEvaluation("flag", defaultValue = false, context = ctx).value)
    }

    @Test
    fun `maps off and false to boolean false`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returnsMany listOf(
            SplitResult("off", null),
            SplitResult("false", null)
        )

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        // default is true; evaluation should force false
        assertEquals(false, evaluator.getBooleanEvaluation("flag", defaultValue = true, context = ctx).value)
        assertEquals(false, evaluator.getBooleanEvaluation("flag", defaultValue = true, context = ctx).value)
    }

    @Test(expected = OpenFeatureError.ParseError::class)
    fun `unknown treatment causes error so default is used by OpenFeature`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("unknown", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        // Expect the evaluator to throw so the SDK uses the default
        evaluator.getBooleanEvaluation("flag", defaultValue = true, context = ctx)
    }

    @Test(expected = OpenFeatureError.TargetingKeyMissingError::class)
    fun `throws when no context provided and no default context`() {
        val client = mockk<SplitClient>(relaxed = true)
        val evaluator = evaluatorWith(client = client, defaultContext = null)

        evaluator.getBooleanEvaluation("flag", defaultValue = false, context = null)
    }

    @Test(expected = OpenFeatureError.ProviderNotReadyError::class)
    fun `throws ProviderNotReady when provided context key differs from current client key`() {
        val client = mockk<SplitClient>(relaxed = true)
        val defaultCtx: EvaluationContext = ImmutableContext(targetingKey = "current-user")
        val evaluator = evaluatorWith(client = client, defaultContext = defaultCtx)

        val otherCtx: EvaluationContext = ImmutableContext(targetingKey = "other-user")
        evaluator.getBooleanEvaluation("flag", defaultValue = true, context = otherCtx)
    }
    @Test
    fun `uses default context from state when context param is null`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("on", null)

        val defaultCtx: EvaluationContext = ImmutableContext(targetingKey = "user-default")
        val evaluator = evaluatorWith(client = client, defaultContext = defaultCtx)

        val eval = evaluator.getBooleanEvaluation("flag", defaultValue = false, context = null)
        assertTrue(eval.value)
    }

    @Test
    fun `string evaluation maps treatment directly`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("some-string", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        val eval = evaluator.getStringEvaluation("flag", defaultValue = "default", context = ctx)
        assertEquals("some-string", eval.value)
    }

    @Test
    fun `integer evaluation parses integer`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("42", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        val eval = evaluator.getIntegerEvaluation("flag", defaultValue = 0, context = ctx)
        assertEquals(42, eval.value)
    }

    @Test(expected = OpenFeatureError.ParseError::class)
    fun `integer evaluation throws on non-numeric`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("NaN", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        evaluator.getIntegerEvaluation("flag", defaultValue = 0, context = ctx)
    }

    @Test
    fun `double evaluation parses double`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("3.14", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        val eval = evaluator.getDoubleEvaluation("flag", defaultValue = 0.0, context = ctx)
        assertEquals(3.14, eval.value, 0.0)
    }

    @Test(expected = OpenFeatureError.ParseError::class)
    fun `double evaluation throws on non-numeric`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("oops", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        evaluator.getDoubleEvaluation("flag", defaultValue = 0.0, context = ctx)
    }

    @Test
    fun `evaluation sets variant to treatment`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("my-variant", null)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        val eval = evaluator.getStringEvaluation("flag", defaultValue = "default", context = ctx)
        assertEquals("my-variant", eval.variant)
    }

    @Test
    fun `evaluation attaches config metadata when present`() {
        val client = mockk<SplitClient>()
        val cfg = "{\"hello\":\"world\"}"
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("on", cfg)

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        val eval = evaluator.getBooleanEvaluation("flag", defaultValue = false, context = ctx)
        // Expect metadata contains the config string under key "config"
        assertEquals(cfg, eval.metadata?.getString("config"))
    }

    @Test
    fun `object evaluation parses treatment JSON`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult(
            """
            { "nested": { "a": 1, "b": [true, {"c": "x"}] } }
            """.trimIndent(),
            null
        )

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        val eval = evaluator.getObjectEvaluation("flag", defaultValue = Value.Null, context = ctx)
        val expected = Value.Structure(
            mapOf(
                "nested" to Value.Structure(
                    mapOf(
                        "a" to Value.Integer(1),
                        "b" to Value.List(listOf(Value.Boolean(true), Value.Structure(mapOf("c" to Value.String("x")))))
                    )
                )
            )
        )
        assertEquals(expected, eval.value)
    }

    @Test(expected = OpenFeatureError.ParseError::class)
    fun `object evaluation throws when treatment is blank`() {
        val client = mockk<SplitClient>()
        every { client.getTreatmentWithConfig("flag", any()) } returns SplitResult("", "ignored")

        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user")
        val evaluator = evaluatorWith(client = client, defaultContext = ctx)

        evaluator.getObjectEvaluation("flag", defaultValue = Value.Null, context = ctx)
    }
}
