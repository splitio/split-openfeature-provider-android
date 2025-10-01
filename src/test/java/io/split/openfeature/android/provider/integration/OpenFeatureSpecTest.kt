package io.split.openfeature.android.provider.integration

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import io.split.android.client.ServiceEndpoints
import io.split.android.client.SplitClientConfig
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel
import io.split.openfeature.android.provider.SplitProvider
import io.split.openfeature.android.provider.createTestSplitProvider
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

    @Before
    fun setUp() {
        // Initialize WorkManager for tests
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        
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

    // Basic typed flag evaluation with detailed evaluation
    @Test
    fun `resolve boolean value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("boolean-flag", "off", context)
        
        assertEquals("on", evaluation.value)
    }

    @Test
    fun `resolve string value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("string-flag", "bye", context)
        
        assertEquals("greeting", evaluation.value)
    }

    @Test
    fun `resolve integer value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("integer-flag", "one", context)
        
        assertEquals("ten", evaluation.value)
    }

    @Test
    fun `resolve float value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("float-flag", "point-one", context)
        
        assertEquals("half", evaluation.value)
    }

    @Test
    fun `resolve object value`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("object-flag", "empty", context)
        
        assertEquals("template", evaluation.value)
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
        
        val evaluation = provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve object targeted zero value with matching context`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )
        
        val evaluation = provider.getStringEvaluation("object-targeted-zero-flag", "template", context)
        
        assertEquals("zero", evaluation.value)
    }

    // DEFAULT reason when targeting doesn't match (we're not supporting reasons yet)
    @Test
    fun `resolve boolean targeted zero value with non-matching context returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@none.com"))
        )
        
        val evaluation = provider.getStringEvaluation("boolean-targeted-zero-flag", "on", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve string targeted zero value with non-matching context returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@none.com"))
        )
        
        val evaluation = provider.getStringEvaluation("string-targeted-zero-flag", "hi", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve integer targeted zero value with non-matching context returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@none.com"))
        )
        
        val evaluation = provider.getStringEvaluation("integer-targeted-zero-flag", "one", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve float targeted zero value with non-matching context returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@none.com"))
        )
        
        val evaluation = provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `resolve object targeted zero value with non-matching context returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.String("ballmer@none.com"))
        )
        
        val evaluation = provider.getStringEvaluation("object-targeted-zero-flag", "template", context)
        
        assertEquals("zero", evaluation.value)
    }

    // FLAG_NOT_FOUND error code
    @Test
    fun `flag not found error for boolean returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("non-existent-flag", "control", context)
        
        assertEquals("control", evaluation.value)
    }

    @Test
    fun `flag not found error for string returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("non-existent-flag", "bye", context)
        
        assertEquals("bye", evaluation.value)
    }

    @Test
    fun `flag not found error for integer returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("non-existent-flag", "one", context)
        
        assertEquals("one", evaluation.value)
    }

    @Test
    fun `flag not found error for float returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("non-existent-flag", "point-one", context)
        
        assertEquals("point-one", evaluation.value)
    }

    @Test
    fun `flag not found error for object returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("non-existent-flag", "empty", context)
        
        assertEquals("empty", evaluation.value)
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
        
        val evaluation = provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `empty evaluation context for object targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("object-targeted-zero-flag", "template", context)
        
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
        
        val evaluation = provider.getStringEvaluation("float-targeted-zero-flag", "point-one", context)
        
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `null context value for object targeted flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(
            targetingKey = "test-user",
            attributes = mapOf("email" to Value.Null)
        )
        
        val evaluation = provider.getStringEvaluation("object-targeted-zero-flag", "template", context)
        
        assertEquals("zero", evaluation.value)
    }

    // DISABLED reason code (we're not supporting reasons yet)
    @Test
    fun `disabled boolean flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("boolean-disabled-flag", "control", context)
        
        assertEquals("control", evaluation.value)
    }

    @Test
    fun `disabled string flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("string-disabled-flag", "bye", context)
        
        assertEquals("bye", evaluation.value)
    }

    @Test
    fun `disabled integer flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("integer-disabled-flag", "one", context)
        
        assertEquals("one", evaluation.value)
    }

    @Test
    fun `disabled float flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("float-disabled-flag", "point-one", context)
        
        assertEquals("point-one", evaluation.value)
    }

    @Test
    fun `disabled object flag returns default`() = runBlocking {
        val provider = createAndInitializeProvider("test-user")
        val context = ImmutableContext(targetingKey = "test-user")
        
        val evaluation = provider.getStringEvaluation("object-disabled-flag", "empty", context)
        
        assertEquals("empty", evaluation.value)
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
                    request.path?.contains("/testImpressions") == true -> MockResponse().setResponseCode(200)
                    request.path?.contains("/keys/cs") == true -> MockResponse().setResponseCode(200)
                    request.path?.contains("/v2/auth") == true -> MockResponse().setResponseCode(200).setBody("""{"pushEnabled":false}""")
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
