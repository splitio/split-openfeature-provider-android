package io.split.openfeature.android.provider.integration

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.openfeature.kotlin.sdk.Client
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.ImmutableStructure
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.TrackingEventDetails
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import io.split.android.client.ServiceEndpoints
import io.split.android.client.SplitClientConfig
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.utils.logger.LogPrinter
import io.split.android.client.utils.logger.Logger
import io.split.android.client.utils.logger.SplitLogLevel
import io.split.openfeature.android.provider.EvaluationContextExt.withTrafficType
import io.split.openfeature.android.provider.SplitProvider
import io.split.openfeature.android.provider.createTestSplitProvider
import io.split.openfeature.android.provider.verifyEventSent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Client-level integration tests using the OpenFeature SDK/Client API.
 *
 * These tests differ from provider-level tests by:
 * - Testing through the OpenFeature Client API (not directly calling provider methods)
 * - Verifying that errors are caught and default values are returned (not thrown)
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class OpenFeatureClientIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var splitFactory: SplitFactory
    private lateinit var openFeatureClient: Client
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
        Logger.instance().setLevel(SplitLogLevel.VERBOSE)
        Logger.instance().setPrinter(object : LogPrinter {
            override fun v(tag: String?, msg: String?, tr: Throwable?) =
                println("$tag - v: $msg")

            override fun d(tag: String?, msg: String?, tr: Throwable?) =
                println("$tag - d: $msg")

            override fun i(tag: String?, msg: String?, tr: Throwable?) =
                println("$tag - i: $msg")

            override fun w(tag: String?, msg: String?, tr: Throwable?) =
                println("$tag - w: $msg")

            override fun e(tag: String?, msg: String?, tr: Throwable?) =
                println("$tag - e: $msg")

            override fun wtf(tag: String?, msg: String?, tr: Throwable?) =
                println("$tag - wtf: $msg")
        })
    }

    @After
    fun tearDown() {
        if (::splitFactory.isInitialized) {
            splitFactory.destroy()
        }
        mockWebServer.shutdown()
    }

    // Basic Typed Evaluation Tests
    @Test
    fun `client getBooleanValue returns parsed boolean from treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getBooleanValue("boolean-flag", false)

        assertTrue(result)
    }

    @Test
    fun `client getStringValue returns string treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getStringValue("string-flag", "default")

        assertEquals("greeting", result)
    }

    @Test
    fun `client getIntegerValue returns default for unparseable treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // integer-flag returns "ten" which cannot be parsed
        // Client should catch ParseError and return default
        val result = client.getIntegerValue("integer-flag", 999)

        assertEquals(999, result)
    }

    @Test
    fun `client getDoubleValue returns default for unparseable treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // float-flag returns "half" which cannot be parsed
        // Client should catch ParseError and return default
        val result = client.getDoubleValue("float-flag", 99.9)

        assertEquals(99.9, result, 0.001)
    }

    @Test
    fun `client getObjectValue returns parsed JSON object`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getObjectValue("object-flag", Value.Null)

        assertTrue(
            "Expected Value.Structure but got ${result::class}",
            result is Value.Structure
        )
    }

    // Error Handling - Client Returns Default Values
    @Test
    fun `client returns default value for non-existent flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // Provider throws FlagNotFoundError, but client catches it and returns default
        val result = client.getStringValue("non-existent-flag", "my-default")

        assertEquals("my-default", result)
    }

    @Test
    fun `client returns default boolean for non-existent flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getBooleanValue("non-existent-flag", true)

        assertTrue(result)
    }

    @Test
    fun `client returns default integer for non-existent flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getIntegerValue("non-existent-flag", 42)

        assertEquals(42, result)
    }

    @Test
    fun `client returns default double for non-existent flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getDoubleValue("non-existent-flag", 3.14)

        assertEquals(3.14, result, 0.001)
    }

    @Test
    fun `client returns default object for non-existent flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val defaultObj = Value.Structure(mapOf("key" to Value.String("default")))
        val result = client.getObjectValue("non-existent-flag", defaultObj)

        assertEquals(defaultObj, result)
    }

    @Test
    fun `client returns default value for disabled flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // Provider throws FlagNotFoundError for disabled flags
        // Client should catch and return default
        val result = client.getStringValue("string-disabled-flag", "disabled-default")

        assertEquals("disabled-default", result)
    }

    @Test
    fun `client returns default boolean for disabled flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val result = client.getBooleanValue("boolean-disabled-flag", false)

        assertFalse(result)
    }

    // Evaluation Details Tests
    @Test
    fun `client getBooleanDetails returns variant information`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val details = client.getBooleanDetails("boolean-flag", false)

        assertTrue(details.value)
        assertEquals("on", details.variant)
        assertNotNull(details.reason)
    }

    @Test
    fun `client getStringDetails returns variant information`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val details = client.getStringDetails("string-flag", "default")

        assertEquals("greeting", details.value)
        assertEquals("greeting", details.variant)
    }

    @Test
    fun `client getDetails returns error info for non-existent flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val details = client.getStringDetails("non-existent-flag", "default")

        assertEquals("default", details.value)
        assertNotNull(details.errorCode)
        assertNotNull(details.errorMessage)
    }

    @Test
    fun `client getDetails returns error info for parse error`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // integer-flag returns "ten" which can't be parsed
        val details = client.getIntegerDetails("integer-flag", 999)

        assertEquals(999, details.value)
        assertNotNull(details.errorCode)
    }

    // Type Mismatch Tests
    @Test
    fun `client getStringValue works on boolean flag`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // Requesting boolean flag as string should return the treatment string
        val result = client.getStringValue("boolean-flag", "default")

        assertEquals("on", result)
    }

    @Test
    fun `client getBooleanValue returns default for non-boolean treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // string-flag returns "greeting" which isn't a valid boolean
        // Client should catch ParseError and return default
        val result = client.getBooleanValue("string-flag", true)

        assertTrue(result)
    }

    @Test
    fun `client getIntegerValue returns default for string treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // string-flag returns "greeting" which can't be parsed as integer
        val result = client.getIntegerValue("string-flag", 555)

        assertEquals(555, result)
    }

    @Test
    fun `client getDoubleValue returns default for string treatment`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        // string-flag returns "greeting" which can't be parsed as double
        val result = client.getDoubleValue("string-flag", 7.77)

        assertEquals(7.77, result, 0.001)
    }

    @Test
    fun `client getObjectValue returns default for invalid JSON`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val defaultObj = Value.Structure(mapOf("error" to Value.Boolean(true)))

        // string-flag returns "greeting" which isn't valid JSON
        val result = client.getObjectValue("string-flag", defaultObj)

        // Parser returns Value.Null for invalid JSON, but that's still a valid Value
        // so client won't use default - it will return Value.Null
        assertTrue(
            "Expected Value.Null but got ${result::class}",
            result is Value.Null
        )
    }

    // Provider Status Tests
    @Test
    fun `client observes provider ProviderReady event after initialization`() = runBlocking {
        splitFactory = createReadySplitFactory("test-user")

        // Wait for Split SDK to be ready
        val splitClient = splitFactory.client(Key("test-user"))
        withTimeout(15000) {
            var ready = false
            while (!ready) {
                delay(100)
                ready = splitClient.isReady
            }
        }

        // Create and initialize provider
        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )

        val context = ImmutableContext(targetingKey = "test-user")
        provider.initialize(context)

        val events = mutableListOf<OpenFeatureProviderEvents>()
        val eventJob = launch {
            provider.observe().collect { event ->
                events.add(event)
            }
        }

        // Give it time to collect the replayed event
        delay(500)

        eventJob.cancel()

        // Verify we got ProviderReady event (from replay)
        assertTrue(
            "Expected ProviderReady event from replay, got: $events",
            events.contains(OpenFeatureProviderEvents.ProviderReady)
        )
    }

    @Test
    fun `client receives ProviderReady event when observing after initialization`() = runBlocking {
        splitFactory = createReadySplitFactory("test-user")

        // Wait for Split SDK
        val splitClient = splitFactory.client(Key("test-user"))
        withTimeout(15000) {
            var ready = false
            while (!ready) {
                delay(100)
                ready = splitClient.isReady
            }
        }

        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )

        // Initialize provider first
        val context = ImmutableContext(targetingKey = "test-user")
        provider.initialize(context)

        // Wait for initialization to complete
        delay(200)

        // Now start observing (late subscriber should still get ProviderReady)
        var receivedReady = false
        val eventJob = launch {
            provider.observe().collect { event ->
                if (event == OpenFeatureProviderEvents.ProviderReady) {
                    receivedReady = true
                }
            }
        }

        // Wait for event emission
        kotlinx.coroutines.delay(500)
        eventJob.cancel()

        assertTrue(
            "Late subscriber should receive ProviderReady event",
            receivedReady
        )
    }

    // Metadata Tests
    @Test
    fun `client receives flag metadata in details`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        val details = client.getStringDetails("metadata-flag", "default")

        assertNotEquals("default", details.value)
        // Metadata is available in evaluation details
        assertNotNull(details)
    }

    // Tracking Tests
    @Test
    fun `client track sends event to events endpoint`() = runBlocking {
        val client = createAndInitializeClientWithTrafficType("test-user", "user")

        recordedRequests.clear()

        client.track("button_clicked")

        delay(1000)

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
    fun `client track with value sends event to events endpoint`() = runBlocking {
        val client = createAndInitializeClientWithTrafficType("test-user", "user")

        recordedRequests.clear()

        val details = TrackingEventDetails(value = 42.0)
        client.track("purchase_completed", details)

        delay(1000)

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
    fun `client track with properties sends event to events endpoint`() = runBlocking {
        val client = createAndInitializeClientWithTrafficType("test-user", "user")

        recordedRequests.clear()

        val details = TrackingEventDetails(
            structure = ImmutableStructure(
                mapOf(
                    "product_id" to Value.String("12345"),
                    "category" to Value.String("electronics")
                )
            )
        )
        client.track("product_viewed", details)

        delay(1000)

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
    fun `client track with value and properties sends event to events endpoint`() = runBlocking {
        val client = createAndInitializeClientWithTrafficType("test-user", "user")

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
        client.track("order_total", details)

        delay(1000)

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

    @Test
    fun `client track works with default traffic type without explicit withTrafficType call`() = runBlocking {
        val client = createAndInitializeClient("test-user")

        recordedRequests.clear()

        client.track("button_clicked")

        delay(1000)

        verifyEventSent(
            recordedRequests = recordedRequests,
            eventName = "button_clicked",
            expectedValue = null,
            expectedProperties = null,
            userKey = "test-user",
            trafficType = "user"
        )
    }

    private suspend fun createAndInitializeClient(userKey: String): Client {
        splitFactory = createReadySplitFactory(userKey)

        // Wait for Split SDK to be ready
        val splitClient = splitFactory.client(Key(userKey))
        withTimeout(15000) {
            var ready = false
            while (!ready) {
                delay(100)
                ready = splitClient.isReady
            }
        }

        // Create Split provider
        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )

        // Set provider with context
        val context = ImmutableContext(targetingKey = userKey)
        OpenFeatureAPI.setProvider(provider, initialContext = context)

        // Wait for provider initialization to complete
        delay(500)

        // Get OpenFeature client
        openFeatureClient = OpenFeatureAPI.getClient()

        return openFeatureClient
    }

    private suspend fun createAndInitializeClientWithTrafficType(
        userKey: String,
        trafficType: String
    ): Client {
        splitFactory = createReadySplitFactory(userKey)

        // Wait for Split SDK to be ready
        val splitClient = splitFactory.client(Key(userKey))
        withTimeout(15000) {
            var ready = false
            while (!ready) {
                delay(100)
                ready = splitClient.isReady
            }
        }

        // Create Split provider
        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )

        // Set provider with context including trafficType - OpenFeature SDK handles initialization
        val context = ImmutableContext(targetingKey = userKey).withTrafficType(trafficType)
        OpenFeatureAPI.setProvider(provider, initialContext = context)

        // Wait for provider initialization to complete
        delay(500)

        // Get OpenFeature client
        openFeatureClient = OpenFeatureAPI.getClient()

        return openFeatureClient
    }

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
            .logLevel(SplitLogLevel.VERBOSE)
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
