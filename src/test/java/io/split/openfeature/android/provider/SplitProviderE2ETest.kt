package io.split.openfeature.android.provider

import androidx.test.core.app.ApplicationProvider
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.SplitResult
import io.split.android.client.api.Key
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end style tests that exercise only the public SplitProvider API.
 * We mock the Split SDK entry point (SplitFactoryBuilder.build) to return a fake
 * factory and a controllable SplitClient.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SplitProviderE2ETest : BaseMockkTest() {

    // A controllable fake client that supports readiness callbacks and dynamic treatments
    private class FakeClient(
        private val responder: (flagKey: String, attributes: Map<String, Any?>) -> SplitResult
    ) : SplitClient by mockk(relaxed = true) {
        private val listeners = mutableMapOf<SplitEvent, MutableList<(SplitClient?) -> Unit>>()

        override fun on(event: SplitEvent?, task: SplitEventTask?) {
            if (event != null && task != null) {
                listeners.getOrPut(event) { mutableListOf() }.add { c -> task.onPostExecution(c) }
            }
        }

        fun fireReady() {
            listeners[SplitEvent.SDK_READY]?.forEach { it(this) }
        }

        fun subscribed(event: SplitEvent): Boolean = listeners.containsKey(event)

        override fun getTreatmentWithConfig(featureFlagName: String?, attributes: MutableMap<String, Any?>?): SplitResult {
            val key = featureFlagName ?: ""
            val attrs = attributes?.toMap() ?: emptyMap()
            return responder(key, attrs)
        }
    }

    private fun withProviderAndClient(
        clientResponder: (flagKey: String, attributes: Map<String, Any?>) -> SplitResult,
        block: suspend (provider: SplitProvider, client: FakeClient) -> Unit
    ) {
        // Mock SplitFactoryBuilder.build to return a factory whose client() returns our FakeClient
        mockkStatic(SplitFactoryBuilder::class)
        val fakeFactory = mockk<SplitFactory>()
        val fakeClient = FakeClient(clientResponder)

        every { SplitFactoryBuilder.build(any(), any(), any(), any()) } returns fakeFactory

        every { fakeFactory.client(any() as String) } returns fakeClient
        every { fakeFactory.client(any() as Key) } returns fakeClient

        val config = SplitProvider.Config(
            applicationContext = ApplicationProvider.getApplicationContext(),
            sdkKey = "test-sdk-key"
        )

        val provider = SplitProvider(config = config)

        // Initialize with a targeting key and then fire SDK_READY so initialization completes
        val ctx = ImmutableContext(targetingKey = "user-1")

        runBlocking {
            val init = async { provider.initialize(ctx) }
            // Wait until the SDK registers the READY listener to avoid missing the event
            while (!fakeClient.subscribed(SplitEvent.SDK_READY)) {
                yield()
            }
            // Now signal readiness
            fakeClient.fireReady()
            init.await()

            block(provider, fakeClient)
        }
    }

    // basic evaluation
    @Test
    fun resolves_boolean_value() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "boolean-flag" -> SplitResult("on", null)
                else -> SplitResult("off", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getBooleanEvaluation("boolean-flag", defaultValue = false, context = null)
        assertTrue(res.value)
    }

    @Test
    fun resolves_string_value() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "string-flag" -> SplitResult("hi", null)
                else -> SplitResult("bye", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getStringEvaluation("string-flag", defaultValue = "bye", context = null)
        assertEquals("hi", res.value)
    }

    @Test
    fun resolves_integer_value() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "integer-flag" -> SplitResult("10", null)
                else -> SplitResult("1", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getIntegerEvaluation("integer-flag", defaultValue = 1, context = null)
        assertEquals(10, res.value)
    }

    @Test
    fun resolves_float_value() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "float-flag" -> SplitResult("0.5", null)
                else -> SplitResult("0.1", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getDoubleEvaluation("float-flag", defaultValue = 0.1, context = null)
        assertEquals(0.5, res.value, 0.0)
    }

    @Test
    fun resolves_object_value() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "object-flag" -> SplitResult(
                    """
                    {"showImages":true,"title":"Check out these pics!","imagesPerPage":100}
                    """.trimIndent(),
                    null
                )
                else -> SplitResult("{}", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getObjectEvaluation("object-flag", defaultValue = Value.Null, context = null)
        val expected = Value.Structure(
            mapOf(
                "showImages" to Value.Boolean(true),
                "title" to Value.String("Check out these pics!"),
                "imagesPerPage" to Value.Integer(100)
            )
        )
        assertEquals(expected, res.value)
    }

    // detailed evaluation (variant is the treatment)
    @Test
    fun resolves_boolean_details() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "boolean-flag" -> SplitResult("on", null)
                else -> SplitResult("off", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getBooleanEvaluation("boolean-flag", defaultValue = false, context = null)
        assertTrue(res.value)
        assertEquals("on", res.variant)
    }

    @Test
    fun resolves_string_details() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "string-flag" -> SplitResult("hi", null)
                else -> SplitResult("bye", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getStringEvaluation("string-flag", defaultValue = "bye", context = null)
        assertEquals("hi", res.value)
        assertEquals("hi", res.variant)
    }

    @Test
    fun resolves_integer_details() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "integer-flag" -> SplitResult("10", null)
                else -> SplitResult("1", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getIntegerEvaluation("integer-flag", defaultValue = 1, context = null)
        assertEquals(10, res.value)
        assertEquals("10", res.variant)
    }

    @Test
    fun resolves_float_details() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "float-flag" -> SplitResult("0.5", null)
                else -> SplitResult("0.1", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getDoubleEvaluation("float-flag", defaultValue = 0.1, context = null)
        assertEquals(0.5, res.value, 0.0)
        assertEquals("0.5", res.variant)
    }

    @Test
    fun resolves_object_details() = withProviderAndClient(
        clientResponder = { flag, _ ->
            when (flag) {
                "object-flag" -> SplitResult(
                    """
                    {"showImages":true,"title":"Check out these pics!","imagesPerPage":100}
                    """.trimIndent(),
                    null
                )
                else -> SplitResult("{}", null)
            }
        }
    ) { provider, _ ->
        val res = provider.getObjectEvaluation("object-flag", defaultValue = Value.Null, context = null)
        val expected = Value.Structure(
            mapOf(
                "showImages" to Value.Boolean(true),
                "title" to Value.String("Check out these pics!"),
                "imagesPerPage" to Value.Integer(100)
            )
        )
        assertEquals(expected, res.value)
        assertEquals("{\"showImages\":true,\"title\":\"Check out these pics!\",\"imagesPerPage\":100}", res.variant)
    }

    // context-aware evaluation
    @Test
    fun resolves_based_on_context_and_empty_context_behavior() = withProviderAndClient(
        clientResponder = { flag, attrs ->
            if (flag == "context-aware") {
                val fn = attrs["fn"] as? String
                val ln = attrs["ln"] as? String
                val age = (attrs["age"] as? Number)?.toInt()
                val customer = (attrs["customer"] as? Boolean)
                if (fn == "Sulisław" && ln == "Świętopełk" && age == 29 && customer == false) {
                    SplitResult("INTERNAL", null)
                } else {
                    SplitResult("EXTERNAL", null)
                }
            } else {
                SplitResult("control", null)
            }
        }
    ) { provider, _ ->
        // Set a context with attributes
        runBlocking {
            val base = ImmutableContext(targetingKey = "user-1")
            val withAttrs = ImmutableContext(
                targetingKey = base.getTargetingKey(),
                mapOf(
                    "fn" to Value.String("Sulisław"),
                    "ln" to Value.String("Świętopełk"),
                    "age" to Value.Integer(29),
                    "customer" to Value.Boolean(false)
                )
            )

            provider.onContextSet(base, withAttrs)

            val res = provider.getStringEvaluation("context-aware", defaultValue = "EXTERNAL", context = withAttrs)
            assertEquals("INTERNAL", res.value)

            // Empty context should fall back to defaultContext (targeting key only), so attributes missing
            // Reset default context back to a key-only context
            provider.onContextSet(withAttrs, base)
            val resEmpty = provider.getStringEvaluation("context-aware", defaultValue = "EXTERNAL", context = null)
            assertEquals("EXTERNAL", resEmpty.value)
        }
    }
}
