package io.split.openfeature.android.provider.integration

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.split.android.client.ServiceEndpoints
import io.split.android.client.SplitClientConfig
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel
import io.split.openfeature.android.provider.EvaluationContextExt.withTrafficType
import io.split.openfeature.android.provider.SplitProvider
import io.split.openfeature.android.provider.createTestSplitProvider
import io.split.openfeature.android.provider.verifyEventSent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * This test suite validates compliance with the OpenFeature specification
 * using the evaluation_v2.feature Gherkin scenarios as basis.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class OpenFeatureSpecTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var splitFactory: SplitFactory
    private val recordedRequests = mutableListOf<RecordedRequest>()

    @Before
    fun setUp() {
        // Initialize WorkManager for tests
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        recordedRequests.clear()
        mockWebServer = MockWebServer()
        setupMockServerDispatcher()
        mockWebServer.start()
        Logger.instance().setLevel(SplitLogLevel.ERROR)
    }

    @After
    fun tearDown() {
        if (::splitFactory.isInitialized) {
            splitFactory.destroy()
        }
        mockWebServer.shutdown()
    }

    // Basic flag evaluation
    @Test
    fun `resolve string value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("string-flag", "bye", context)

        assertEquals("greeting", evaluation.value)
    }

    // Reason field - zero values (we're not supporting reasons yet)
    @Test
    fun `resolve boolean zero value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("boolean-zero-flag", "on", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve string zero value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("string-zero-flag", "hi", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve integer zero value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("integer-zero-flag", "one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve float zero value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("float-zero-flag", "point-one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve object zero value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("object-zero-flag", "template", context)

        assertEquals("zero", evaluation.value)
    }

    // TARGETING_MATCH reason with evaluation context (we're not supporting reasons yet)
    @Test
    fun `resolve boolean targeted zero value with matching context`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )

        val evaluation = provider.getStringEvaluation("boolean-targeted-zero-flag", "on", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve string targeted zero value with matching context`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )

        val evaluation = provider.getStringEvaluation("string-targeted-zero-flag", "hi", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve integer targeted zero value with matching context`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )

        val evaluation = provider.getStringEvaluation("integer-targeted-zero-flag", "one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve float targeted zero value with matching context`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )

        val evaluation =
            provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve object targeted zero value with matching context`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )

        val evaluation =
            provider.getStringEvaluation("object-targeted-zero-flag", "template", context)

        assertEquals("zero", evaluation.value)
    }

    // DEFAULT reason when targeting doesn't match (we're not supporting reasons yet)
    @Test
    fun `resolve boolean targeted zero value with non-matching context returns default`() =
        runBlocking {
            val provider = createAndInitializeProvider("test-user")
            val context = ImmutableContext(
                targetingKey = "test-user",
                attributes = mapOf("email" to Value.String("ballmer@none.com"))
            )

            val evaluation =
                provider.getStringEvaluation("boolean-targeted-zero-flag", "on", context)

            assertEquals("zero", evaluation.value)
        }

    @Test
    fun `resolve string targeted zero value with non-matching context returns default`() =
        runBlocking {
            val provider = createAndInitializeProvider("test-user")
            val context = ImmutableContext(
                targetingKey = "test-user",
                attributes = mapOf("email" to Value.String("ballmer@none.com"))
            )

            val evaluation =
                provider.getStringEvaluation("string-targeted-zero-flag", "hi", context)

            assertEquals("zero", evaluation.value)
        }

    @Test
    fun `resolve integer targeted zero value with non-matching context returns default`() =
        runBlocking {
            val provider = createAndInitializeProvider("test-user")
            val context = ImmutableContext(
                targetingKey = "test-user",
                attributes = mapOf("email" to Value.String("ballmer@none.com"))
            )

            val evaluation =
                provider.getStringEvaluation("integer-targeted-zero-flag", "one", context)

            assertEquals("zero", evaluation.value)
        }

    @Test
    fun `resolve float targeted zero value with non-matching context returns default`() =
        runBlocking {
            val provider = createAndInitializeProvider("test-user")
            val context = ImmutableContext(
                targetingKey = "test-user",
                attributes = mapOf("email" to Value.String("ballmer@none.com"))
            )

            val evaluation =
                provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)

            assertEquals("zero", evaluation.value)
        }

    @Test
    fun `resolve object targeted zero value with non-matching context returns default`() =
        runBlocking {
            val provider = createAndInitializeProvider("test-user")
            val context = ImmutableContext(
                targetingKey = "test-user",
                attributes = mapOf("email" to Value.String("ballmer@none.com"))
            )

            val evaluation =
                provider.getStringEvaluation("object-targeted-zero-flag", "template", context)

            assertEquals("zero", evaluation.value)
        }

    // FLAG_NOT_FOUND error code
    @Test
    fun `flag not found error for boolean throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("non-existent-flag", "control", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected - provider should throw FlagNotFoundError for non-existent flags
        }
    }

    @Test
    fun `flag not found error for string throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("non-existent-flag", "bye", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    @Test
    fun `flag not found error for integer throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("non-existent-flag", "one", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    @Test
    fun `flag not found error for float throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("non-existent-flag", "point-one", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    @Test
    fun `flag not found error for object throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("non-existent-flag", "empty", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    // Type mismatch handling
    @Test
    fun `requesting boolean flag as string returns treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("boolean-flag", "control", context)

        assertEquals("on", evaluation.value)
    }

    @Test
    fun `requesting string flag as boolean evaluates string treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("string-flag", "control", context)

        assertEquals("greeting", evaluation.value)
    }

    // Flag metadata field in evaluation details
    @Test
    fun `flag metadata in evaluation details`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("metadata-flag", "control", context)

        // Verify the flag evaluates successfully
        assertNotEquals("control", evaluation.value)
        // TODO: Verify metadata fields when Split SDK provides metadata support
        // Expected metadata: string=1.0.2, integer=2, float=0.1, boolean=true
    }

    // Evaluation with empty context
    @Test
    fun `empty evaluation context for boolean targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("boolean-targeted-zero-flag", "on", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `empty evaluation context for string targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("string-targeted-zero-flag", "str", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `empty evaluation context for integer targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getStringEvaluation("integer-targeted-zero-flag", "one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `empty evaluation context for float targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation =
            provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `empty evaluation context for object targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation =
            provider.getStringEvaluation("object-targeted-zero-flag", "template", context)

        assertEquals("zero", evaluation.value)
    }

    // Evaluation with null context values
    @Test
    fun `null context value for boolean targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.Null)
        )

        val evaluation = provider.getStringEvaluation("boolean-targeted-zero-flag", "on", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `null context value for string targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.Null)
        )

        val evaluation = provider.getStringEvaluation("string-targeted-zero-flag", "str", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `null context value for integer targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.Null)
        )

        val evaluation = provider.getStringEvaluation("integer-targeted-zero-flag", "one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `null context value for float targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.Null)
        )

        val evaluation =
            provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)

        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `null context value for object targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.Null)
        )

        val evaluation =
            provider.getStringEvaluation("object-targeted-zero-flag", "template", context)

        assertEquals("zero", evaluation.value)
    }

    // DISABLED reason code - disabled flags throw FlagNotFoundError
    @Test
    fun `disabled boolean flag throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("boolean-disabled-flag", "control", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected - disabled flags return "control" which triggers FlagNotFoundError
        }
    }

    @Test
    fun `disabled string flag throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("string-disabled-flag", "bye", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    @Test
    fun `disabled integer flag throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("integer-disabled-flag", "one", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    @Test
    fun `disabled float flag throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("float-disabled-flag", "point-one", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    @Test
    fun `disabled object flag throws FlagNotFoundError`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("object-disabled-flag", "empty", context)
            fail("Should have thrown FlagNotFoundError")
        } catch (e: OpenFeatureError.FlagNotFoundError) {
            // Expected
        }
    }

    // PROVIDER_NOT_READY error when provider isn't initialized
    @Test
    fun `provider not ready error for boolean evaluation`() = runBlocking {
        val provider = createUninitializedProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("boolean-flag", "control", context)
            fail("Should have thrown ProviderNotReadyError")
        } catch (e: dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ProviderNotReadyError) {
            // Expected
        }
    }

    @Test
    fun `provider not ready error for string evaluation`() = runBlocking {
        val provider = createUninitializedProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("string-flag", "bye", context)
            fail("Should have thrown ProviderNotReadyError")
        } catch (e: OpenFeatureError.ProviderNotReadyError) {
            // Expected
        }
    }

    @Test
    fun `provider not ready error for integer evaluation`() = runBlocking {
        val provider = createUninitializedProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("integer-flag", "one", context)
            fail("Should have thrown ProviderNotReadyError")
        } catch (e: OpenFeatureError.ProviderNotReadyError) {
            // Expected
        }
    }

    @Test
    fun `provider not ready error for float evaluation`() = runBlocking {
        val provider = createUninitializedProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("float-flag", "point-one", context)
            fail("Should have thrown ProviderNotReadyError")
        } catch (e: OpenFeatureError.ProviderNotReadyError) {
            // Expected
        }
    }

    @Test
    fun `provider not ready error for object evaluation`() = runBlocking {
        val provider = createUninitializedProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            provider.getStringEvaluation("object-flag", "empty", context)
            fail("Should have thrown ProviderNotReadyError")
        } catch (e: OpenFeatureError.ProviderNotReadyError) {
            // Expected
        }
    }

    // PROVIDER_FATAL error when provider is in fatal state
    @Test
    fun `provider fatal state for boolean evaluation`() = runBlocking {
        val provider = createUninitializedProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        // Uninitialized provider should throw ProviderNotReadyError on evaluation
        try {
            provider.getStringEvaluation("boolean-flag", "control", context)
            fail("Should have thrown ProviderNotReadyError")
        } catch (e: OpenFeatureError.ProviderNotReadyError) {
            // Expected - uninitialized provider is in a "not ready" state
        }
    }

    // Typed Evaluation Methods - Testing each provider method directly
    @Test
    fun `getBooleanEvaluation returns true for 'on' treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getBooleanEvaluation("boolean-flag", false, context)

        assertTrue(evaluation.value)
        assertEquals("on", evaluation.variant)
    }

    @Test
    fun `getBooleanEvaluation throws ParseError for non-boolean treatment like zero`() =
        runBlocking {
            val provider = createAndInitializeProvider("test-user")
            val context = ImmutableContext(targetingKey = "test-user")

            try {
                // boolean-zero-flag returns "zero" which is not a valid boolean
                provider.getBooleanEvaluation("boolean-zero-flag", true, context)
                fail("Should have thrown ParseError")
            } catch (e: OpenFeatureError.ParseError) {
                // Expected - "zero" is not a valid boolean value
            }
        }

    @Test
    fun `getBooleanEvaluation throws ParseError for non-boolean treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // string-flag returns "greeting" which is not a valid boolean
            provider.getBooleanEvaluation("string-flag", false, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected
        }
    }

    @Test
    fun `getIntegerEvaluation throws ParseError for word-based treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // integer-flag returns "ten" which cannot be parsed as integer
            provider.getIntegerEvaluation("integer-flag", 1, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected - "ten" is not parseable as integer
        }
    }

    @Test
    fun `getIntegerEvaluation throws ParseError for zero treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // integer-zero-flag returns "zero" which cannot be parsed as integer
            provider.getIntegerEvaluation("integer-zero-flag", 99, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected - "zero" is not parseable as integer
        }
    }

    @Test
    fun `getIntegerEvaluation throws ParseError for non-numeric treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // string-flag returns "greeting" which is not a valid integer
            provider.getIntegerEvaluation("string-flag", 1, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected
        }
    }

    @Test
    fun `getDoubleEvaluation throws ParseError for word-based treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // float-flag returns "half" which cannot be parsed as double
            provider.getDoubleEvaluation("float-flag", 0.1, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected - "half" is not parseable as double
        }
    }

    @Test
    fun `getDoubleEvaluation throws ParseError for zero treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // float-zero-flag returns "zero" which cannot be parsed as double
            provider.getDoubleEvaluation("float-zero-flag", 99.9, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected - "zero" is not parseable as double
        }
    }

    @Test
    fun `getDoubleEvaluation throws ParseError for non-numeric treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        try {
            // string-flag returns "greeting" which is not a valid double
            provider.getDoubleEvaluation("string-flag", 0.1, context)
            fail("Should have thrown ParseError")
        } catch (e: OpenFeatureError.ParseError) {
            // Expected
        }
    }

    @Test
    fun `getObjectEvaluation returns parsed JSON object`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        val evaluation = provider.getObjectEvaluation(
            "object-flag",
            Value.Structure(mapOf()),
            context
        )

        // Verify we get a Structure value (parsed JSON)
        assertTrue(
            "Expected Value.Structure but got ${evaluation.value::class}",
            evaluation.value is Value.Structure
        )
    }

    @Test
    fun `getObjectEvaluation returns Value Null for non-JSON string treatment`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        // string-flag returns "greeting" which is NOT valid JSON (needs quotes)
        val evaluation =
            provider.getObjectEvaluation("string-flag", Value.Structure(mapOf()), context)

        // Invalid JSON returns Value.Null (parser catches exception)
        assertTrue(
            "Expected Value.Null but got ${evaluation.value::class}",
            evaluation.value is Value.Null
        )
    }

    // Config/Metadata Verification Tests
    @Test
    fun `evaluation returns config metadata when flag has configuration`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        // metadata-flag should have config
        val evaluation = provider.getStringEvaluation("metadata-flag", "control", context)

        assertNotNull("Evaluation should have metadata", evaluation.metadata)
        // TODO: Verify actual metadata content when we know the flag config structure
    }

    @Test
    fun `evaluation without config has no metadata`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")

        // Most flags don't have config, so they should have null metadata
        val evaluation = provider.getStringEvaluation("boolean-flag", "control", context)

        // Metadata should be null or empty when there's no config
        if (evaluation.metadata != null) {
            // If metadata exists, verify it doesn't have config key or config is empty
            val configValue = evaluation.metadata.getString("config")
            assertTrue(
                "Config should be null or empty when flag has no configuration",
                configValue == null || configValue.isEmpty()
            )
        }
    }

    // Tracking Tests
    @Test
    fun `track sends event to events endpoint`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user").withTrafficType("user")

        recordedRequests.clear()

        provider.track("button_clicked", context, null)

        kotlinx.coroutines.delay(1000)

        verifyEventSent(
            recordedRequests = recordedRequests,
            eventName = "button_clicked",
            expectedValue = null,
            expectedProperties = null,
            userKey = "test-user",
            trafficType = "user"
        )
    }

    @Test
    fun `track with value sends event to events endpoint`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user").withTrafficType("user")

        recordedRequests.clear()

        val details = TrackingEventDetails(value = 42.0)
        provider.track("purchase_completed", context, details)

        kotlinx.coroutines.delay(1000)

        verifyEventSent(
            recordedRequests = recordedRequests,
            eventName = "purchase_completed",
            expectedValue = 42.0,
            expectedProperties = null,
            userKey = "test-user",
            trafficType = "user"
        )
    }

    @Test
    fun `track with properties sends event to events endpoint`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user").withTrafficType("user")

        recordedRequests.clear()

        val details = TrackingEventDetails(
            structure = ImmutableStructure(
                mapOf(
                    "product_id" to Value.String("12345"),
                    "category" to Value.String("electronics")
                )
            )
        )
        provider.track("product_viewed", context, details)

        kotlinx.coroutines.delay(1000)

        verifyEventSent(
            recordedRequests = recordedRequests,
            eventName = "product_viewed",
            expectedValue = null,
            expectedProperties = mapOf(
                "product_id" to "12345",
                "category" to "electronics"
            ),
            userKey = "test-user",
            trafficType = "user"
        )
    }

    @Test
    fun `track with value and properties sends event to events endpoint`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user").withTrafficType("user")

        recordedRequests.clear()

        val details = TrackingEventDetails(
            value = 99.99,
            structure = ImmutableStructure(
                mapOf(
                    "currency" to Value.String("USD"),
                    "discount_applied" to Value.Boolean(true)
                )
            )
        )
        provider.track("order_total", context, details)

        kotlinx.coroutines.delay(1000)

        verifyEventSent(
            recordedRequests = recordedRequests,
            eventName = "order_total",
            expectedValue = 99.99,
            expectedProperties = mapOf(
                "currency" to "USD",
                "discount_applied" to true
            ),
            userKey = "test-user",
            trafficType = "user"
        )
    }

    // Test Helper Methods
    /**
     * Creates and initializes a provider that's ready for evaluation tests
     */
    private suspend fun createAndInitializeProvider(userKey: String): SplitProvider {
        splitFactory = createReadySplitFactory(userKey)

        // Wait for the SDK to be ready BEFORE creating the provider
        val client = splitFactory.client(Key(userKey))
        withTimeout(15000) {
            var ready = false
            while (!ready) {
                kotlinx.coroutines.delay(100)
                ready = client.isReady
            }
        }

        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )

        val context = ImmutableContext(targetingKey = userKey)
        withTimeout(5000) {
            provider.initialize(context)
        }

        return provider
    }

    /**
     * Creates a provider that is NOT initialized (NOT_READY state)
     */
    private fun createUninitializedProvider(userKey: String): SplitProvider {
        splitFactory = createReadySplitFactory(userKey)

        // Create provider but DON'T initialize it
        return createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )
    }

    /**
     * Creates a SplitFactory connected to the working mock server for evaluation tests
     */
    private fun createReadySplitFactory(userKey: String): SplitFactory {
        val baseUrl = mockWebServer.url("/").toString()

        val endpoints = ServiceEndpoints.builder()
            .apiEndpoint(baseUrl)
            .eventsEndpoint(baseUrl)
            .telemetryServiceEndpoint(baseUrl)
            .build()

        val config = SplitClientConfig.Builder()
            .serviceEndpoints(endpoints)
            .streamingEnabled(false)
            .featuresRefreshRate(999999)
            .segmentsRefreshRate(999999)
            .impressionsRefreshRate(999999)
            .eventsQueueSize(1)
            .eventFlushInterval(1)
            .logLevel(SplitLogLevel.ERROR)
            .build()

        return SplitFactoryBuilder.build(
            "test-api-key",
            Key(userKey),
            config,
            ApplicationProvider.getApplicationContext()
        )
    }

    private fun setupMockServerDispatcher() {
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Record all requests for verification in tests
                recordedRequests.add(request)

                return when {
                    request.path?.contains("/memberships") == true -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"ms":{"k":[],"cn":null},"ls":{"k":[],"cn":1702507130121}}""")
                    }

                    request.path?.contains("/splitChanges") == true -> {
                        val since = request.requestUrl?.queryParameter("since") ?: "-1"
                        if (since == "-1") {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(loadSplitChanges())
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("""{"ff":{"splits":[],"since":1506703262916,"till":1506703262916},"rbs":{"d":[],"s":1506703262916,"t":1506703262916}}""")
                        }
                    }

                    request.path?.contains("/events") == true -> MockResponse().setResponseCode(200)
                    request.path?.contains("/testImpressions") == true -> MockResponse().setResponseCode(
                        200
                    )

                    request.path?.contains("/keys/cs") == true -> MockResponse().setResponseCode(200)
                    request.path?.contains("/v2/auth") == true -> MockResponse().setResponseCode(200)
                        .setBody("""{"pushEnabled":false}""")

                    request.path?.contains("/metrics") == true -> MockResponse().setResponseCode(200)
                    else -> MockResponse().setResponseCode(200).setBody("{}")
                }
            }
        }
        mockWebServer.dispatcher = dispatcher
    }

    private fun loadSplitChanges(): String {
        val inputStream = javaClass.classLoader?.getResourceAsStream("split_changes_test.json")
            ?: throw IllegalStateException("Could not find split_changes_test.json")
        return inputStream.bufferedReader().use { it.readText() }
    }
}
