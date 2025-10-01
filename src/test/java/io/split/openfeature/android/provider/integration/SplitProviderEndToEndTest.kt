package io.split.openfeature.android.provider.integration

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import io.split.android.client.ServiceEndpoints
import io.split.android.client.SplitClientConfig
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.api.Key
import io.split.android.client.impressions.Impression
import io.split.android.client.impressions.ImpressionListener
import io.split.android.client.utils.logger.LogPrinter
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class SplitProviderEndToEndTest {

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
        Logger.instance().setLevel(SplitLogLevel.VERBOSE)
        Logger.instance().setPrinter(object: LogPrinter {
            override fun v(tag: String?, msg: String?, tr: Throwable?) = println("Logger v: $msg")
            override fun d(tag: String?, msg: String?, tr: Throwable?) = println("Logger d: $msg")
            override fun i(tag: String?, msg: String?, tr: Throwable?) = println("Logger i: $msg")
            override fun w(tag: String?, msg: String?, tr: Throwable?) = println("Logger w: $msg")
            override fun e(tag: String?, msg: String?, tr: Throwable?) = println("Logger e: $msg")
            override fun wtf(tag: String?, msg: String?, tr: Throwable?) = println("Logger wtf: $msg")
        })
    }

    @After
    fun tearDown() {
        if (::splitFactory.isInitialized) {
            splitFactory.destroy()
        }
        mockWebServer.shutdown()
    }

    @Test
    fun `provider initializes with mocked Split SDK`() = runBlocking {
        // Create SplitFactory pointing to mock server
        splitFactory = createSplitFactory("test-user-key")

        // Get client before provider initialization
        val client = splitFactory.client(Key("test-user-key"))
        println("Initial client.isReady: ${client.isReady}")

        // Create provider with injected factory using helper
        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )

        // Initialize provider with context
        val context = ImmutableContext(targetingKey = "test-user-key")
        
        // Initialize with timeout
        withTimeout(10000) {
            provider.initialize(context)
        }

        println("After provider.initialize, client.isReady: ${client.isReady}")
        
        // Verify SDK client is ready
        assertTrue("Split client should be ready", client.isReady)
    }

    @Test
    fun `provider evaluates boolean flag correctly`() = runBlocking {
        val provider = createAndInitializeProvider("test-user-key-1")

        // Evaluate boolean-flag which should return "on"
        val context = ImmutableContext(targetingKey = "test-user-key-1")
        val evaluation = provider.getStringEvaluation("boolean-flag", "default", context)
        println("Evaluation result: value=${evaluation.value}, reason=${evaluation.reason}, errorCode=${evaluation.errorCode}")
        assertEquals("Expected treatment 'on' but got '${evaluation.value}'", "on", evaluation.value)
    }

    @Test
    fun `provider evaluates string flag correctly`() = runBlocking {
        val provider = createAndInitializeProvider("test-user-key-2")

        // Evaluate string-flag which should return "greeting"
        val context = ImmutableContext(targetingKey = "test-user-key-2")
        val evaluation = provider.getStringEvaluation("string-flag", "default", context)
        assertEquals("greeting", evaluation.value)
    }

    @Test
    fun `provider evaluates integer flag correctly`() = runBlocking {
        val provider = createAndInitializeProvider("test-user-key-3")

        // Evaluate integer-flag which should return "ten"
        val context = ImmutableContext(targetingKey = "test-user-key-3")
        val evaluation = provider.getStringEvaluation("integer-flag", "default", context)
        assertEquals("ten", evaluation.value)
    }

    @Test
    fun `provider evaluates targeted flag with matching attributes`() = runBlocking {
        val provider = createAndInitializeProvider("test-user-key-4")

        // Context with email attribute matching targeting rule
        val context = ImmutableContext(
            targetingKey = "test-user-key-4",
            attributes = mapOf("email" to Value.String("ballmer@macrosoft.com"))
        )

        // Evaluate boolean-targeted-zero-flag with matching email
        val evaluation = provider.getStringEvaluation("boolean-targeted-zero-flag", "default", context)
        assertEquals("zero", evaluation.value)
    }

    @Test
    fun `provider evaluates targeted flag with non-matching attributes`() = runBlocking {
        val provider = createAndInitializeProvider("test-user-key-5")

        // Context with email attribute NOT matching targeting rule
        val context = ImmutableContext(
            targetingKey = "test-user-key-5",
            attributes = mapOf("email" to Value.String("other@example.com"))
        )

        // Evaluate boolean-targeted-zero-flag with non-matching email
        // Should fall through to default rule which also returns "zero"
        val evaluation = provider.getStringEvaluation("boolean-targeted-zero-flag", "default", context)
        assertEquals("zero", evaluation.value)
    }

    private suspend fun createAndInitializeProvider(userKey: String): SplitProvider {
        splitFactory = createSplitFactory(userKey)
        println("Created factory for $userKey")
        
        val provider = createTestSplitProvider(
            splitFactory = splitFactory,
            config = SplitProvider.Config(
                applicationContext = ApplicationProvider.getApplicationContext(),
                sdkKey = "test-api-key"
            )
        )
        println("Created provider for $userKey")

        val context = ImmutableContext(targetingKey = userKey)
        println("Initializing provider for $userKey")
        withTimeout(15000) {
            provider.initialize(context)
        }
        println("Provider initialized for $userKey")

        // Give SDK a moment to fully process
        kotlinx.coroutines.delay(100)

        // Verify client is ready
        val client = splitFactory.client(Key(userKey))
        println("Client isReady for $userKey: ${client.isReady}")
        
        // Try to get a treatment directly from SDK to verify it's working
        val treatment = client.getTreatment("boolean-flag")
        println("Direct SDK treatment for boolean-flag: $treatment")
        
        assertTrue("Client should be ready for $userKey", client.isReady)

        return provider
    }

    private fun createSplitFactory(userKey: String): SplitFactory {
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
            .logLevel(SplitLogLevel.VERBOSE)
            .impressionListener(object: ImpressionListener {
                override fun log(impression: Impression?) {
                    if (impression == null) {
                        println("Null impression received")
                    }
                    println("Logging impression for ${impression?.split()}, with treatment ${impression?.treatment()} and label ${impression?.appliedRule()}")
                }

                override fun close() {
                    TODO("Not yet implemented")
                }
            })
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
                println("MockWebServer received: ${request.method} ${request.path}")
                return when {
                    request.path?.contains("/memberships") == true -> {
                        // MySegments endpoint
                        println("Returning empty segments")
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(emptyAllSegments())
                    }
                    request.path?.contains("/splitChanges") == true -> {
                        // Split changes endpoint
                        val since = extractSinceParameter(request)
                        println("Returning splitChanges for since=$since")
                        if (since == "-1") {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(loadSplitChanges())
                        } else {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody(emptySplitChanges())
                        }
                    }
                    request.path?.contains("/events") == true -> {
                        MockResponse().setResponseCode(200)
                    }
                    request.path?.contains("/testImpressions/count") == true -> {
                        MockResponse().setResponseCode(200)
                    }
                    request.path?.contains("/testImpressions/bulk") == true -> {
                        MockResponse().setResponseCode(200)
                    }
                    request.path?.contains("/keys/cs") == true -> {
                        MockResponse().setResponseCode(200)
                    }
                    request.path?.contains("/v2/auth") == true -> {
                        // Auth endpoint for streaming (not used in our tests but SDK may call it)
                        MockResponse().setResponseCode(200).setBody("""{"pushEnabled":false}""")
                    }
                    request.path?.contains("/metrics") == true -> {
                        // Telemetry metrics
                        MockResponse().setResponseCode(200)
                    }
                    else -> {
                        println("No handler for path: ${request.path}")
                        MockResponse().setResponseCode(200).setBody("{}")
                    }
                }
            }
        }
        mockWebServer.dispatcher = dispatcher
    }

    private fun extractSinceParameter(request: RecordedRequest): String {
        val url = request.requestUrl ?: return "-1"
        return url.queryParameter("since") ?: "-1"
    }

    private fun emptyAllSegments(): String {
        return """{"ms":{"k":[],"cn":null},"ls":{"k":[],"cn":1702507130121}}"""
    }

    private fun emptySplitChanges(): String {
        return """{"ff":{"splits":[],"since":1506703262916,"till":1506703262916},"rbs":{"d":[],"s":1506703262916,"t":1506703262916}}"""
    }

    private fun loadSplitChanges(): String {
        // TODO: Load from file or use inline JSON
        return """
        {
          "ff": {
            "splits": [],
            "since": -1,
            "till": 1506703262916
          },
          "rbs": {
            "d": [],
            "s": 1506703262916,
            "t": 1506703262916
          }
        }
        """.trimIndent()
    }
}
