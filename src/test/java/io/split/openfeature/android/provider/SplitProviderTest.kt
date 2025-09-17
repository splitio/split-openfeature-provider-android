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
import io.mockk.verify
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

    @Test
    fun `onContextSet does not do anything if not initialized`() = runTest(testDispatcher) {
        val sdkInitializer = mockk<SdkInitializer>()
        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        provider.onContextSet(null, ImmutableContext(targetingKey = "user-1"))
        coVerify(exactly = 0) { sdkInitializer.initialize(any(), any(), any(), any()) }
    }

    @Test
    fun `onContextSet does not do anything if new context is equal to old context`() = runTest(testDispatcher) {
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), any(), any()) } returns (mockk<SplitFactory>() to mockk<SplitClient>())
        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val ctx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(ctx)
        provider.onContextSet(ctx, ctx)
        coVerify(exactly = 1) { sdkInitializer.initialize(any(), any(), any(), any()) }
        coVerify(exactly = 0) { sdkInitializer.getReadyClient(any(), any(), any()) }
    }

    @Test
    fun `onContextSet fetches a new client if targeting key changes`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val client2 = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), "user-1", any()) } returns (factory to client)
        coEvery { sdkInitializer.getReadyClient(factory, "user-2", any()) } returns client2
        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val ctx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(ctx)
        provider.onContextSet(ctx, ImmutableContext(targetingKey = "user-2"))
        coVerify(exactly = 1) { sdkInitializer.initialize(any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            sdkInitializer.getReadyClient(factory, "user-2", any())
        }
    }

    @Test
    fun `onContextSet does not recreate client when targeting key is unchanged`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), "user-1", any()) } returns (factory to client)

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val oldCtx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(oldCtx)

        val newCtxSameKey = ImmutableContext(targetingKey = "user-1")
        provider.onContextSet(oldCtx, newCtxSameKey)

        // Should not attempt to fetch a new client when the key is the same
        coVerify(exactly = 0) { sdkInitializer.getReadyClient(any(), any(), any()) }
        // And initialize should have been called just once
        coVerify(exactly = 1) { sdkInitializer.initialize(any(), any(), any(), any()) }
    }

    @Test(expected = ProviderFatalError::class)
    fun `onContextSet throws when new context is missing targeting key`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), "user-1", any()) } returns (factory to client)

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val oldCtx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(oldCtx)

        val newCtxMissingKey: EvaluationContext = ImmutableContext() // missing targeting key
        provider.onContextSet(oldCtx, newCtxMissingKey)

        // Should not attempt to get a new client
        coVerify(exactly = 0) { sdkInitializer.getReadyClient(any(), any(), any()) }
    }

    @Test(expected = ProviderNotReadyError::class)
    fun `onContextSet maps IllegalStateException from getReadyClient to ProviderNotReadyError`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), "user-1", any()) } returns (factory to client)
        coEvery { sdkInitializer.getReadyClient(factory, "user-2", any()) } throws IllegalStateException("already built")

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val oldCtx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(oldCtx)

        val newCtx = ImmutableContext(targetingKey = "user-2")
        provider.onContextSet(oldCtx, newCtx) // expect ProviderNotReadyError
    }

    @Test(expected = ProviderFatalError::class)
    fun `onContextSet maps unexpected exceptions from getReadyClient to ProviderFatalError`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkInitializer = mockk<SdkInitializer>()
        coEvery { sdkInitializer.initialize(any(), any(), "user-1", any()) } returns (factory to client)
        coEvery { sdkInitializer.getReadyClient(factory, "user-2", any()) } throws RuntimeException("boom")

        val provider = SplitProvider(config = testConfig(), sdkInitializer = sdkInitializer)
        val oldCtx = ImmutableContext(targetingKey = "user-1")
        provider.initialize(oldCtx)

        val newCtx = ImmutableContext(targetingKey = "user-2")
        provider.onContextSet(oldCtx, newCtx) // expect ProviderFatalError
    }

    private fun getProvider(): SplitProvider = SplitProvider(config = testConfig())

    private fun testConfig(): SplitProvider.Config =
        SplitProvider.Config(
            applicationContext = ApplicationProvider.getApplicationContext(),
            sdkKey = "test-api-key"
        )
}
