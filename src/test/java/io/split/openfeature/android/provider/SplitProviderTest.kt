package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class SplitProviderTest : BaseMockkTest() {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `name is Split`() {
        val provider = SplitProvider(config = testConfig())
        assertEquals("Split", provider.metadata.name)
    }

    @Test
    fun `SplitProvider implements FeatureProvider`() {
        assertTrue(getProvider() is FeatureProvider)
    }

    @Test
    fun `initialize needs a Config`() {
        assertNotNull(getProvider())
    }

    @Test(expected = ProviderFatalError::class)
    fun `initialize throws ProviderFatalError when targeting key is missing`() = runTest(testDispatcher) {
        val provider = SplitProvider(dispatcher = testDispatcher, config = testConfig())
        val ctx: EvaluationContext = ImmutableContext() // no targeting key set
        provider.initialize(ctx)
    }

    @Test(expected = ProviderFatalError::class)
    fun `initialize throws ProviderFatalError when evaluationContext is null`() = runTest(testDispatcher) {
        val provider = SplitProvider(dispatcher = testDispatcher, config = testConfig())
        val ctx: EvaluationContext? = null
        provider.initialize(ctx)
    }

    @Test
    fun `initialize completes successfully when SDK_READY fires`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), any(), any()) } returns (factory to client)

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val ctx = ImmutableContext(targetingKey = "user-1")

        val job = async { provider.initialize(ctx) }
        // allow listeners registration
        runCurrent()
        // should complete without throwing
        job.await()
    }

    @Test
    fun `initialize is idempotent`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), any(), any()) } returns (factory to client)

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val ctx1 = ImmutableContext(targetingKey = "user-1")
        val ctx2 = ImmutableContext(targetingKey = "user-2")

        // First initialize should call the initializer
        provider.initialize(ctx1)

        // Second initialize should no-op and not call initializer again
        provider.initialize(ctx2)

        coVerify(exactly = 1) { sdkInitializer.initialize(any(), any(), any(), any()) }
    }

    @Test(expected = CancellationException::class)
    fun `initialize propagates cancellation`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), any(), any()) } coAnswers {
            delay(10_000)
            factory to client
        }

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val ctx = ImmutableContext(targetingKey = "user-1")

        val job = async { provider.initialize(ctx) }
        // allow listeners registration
        runCurrent()
        // cancel before READY
        job.cancel()
        job.await() // should surface CancellationException
    }

    @Test(expected = ProviderNotReadyError::class)
    fun `initialize maps IllegalStateException to ProviderNotReadyError`() = runTest(testDispatcher) {
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), any(), any()) } throws IllegalStateException("already built")

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val ctx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(ctx)
    }

    private fun getProvider(): SplitProvider = SplitProvider(config = testConfig())

    private fun testConfig(): SplitProvider.Config =
        SplitProvider.Config(
            applicationContext = ApplicationProvider.getApplicationContext(),
            sdkKey = "test-api-key"
        )
}
