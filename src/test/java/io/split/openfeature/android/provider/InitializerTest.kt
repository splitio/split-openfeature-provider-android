package io.split.openfeature.android.provider

import androidx.test.core.app.ApplicationProvider
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ProviderFatalError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ProviderNotReadyError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class InitializerTest : BaseMockkTest() {

    private val testDispatcher = StandardTestDispatcher()

    @Test(expected = ProviderFatalError::class)
    fun `initialize throws ProviderFatalError when targeting key is missing`() =
        runTest(testDispatcher) {
            val initializer = initializer()
            val ctx: EvaluationContext = ImmutableContext() // no targeting key set
            initializer.initialize(ctx)
        }

    @Test(expected = ProviderFatalError::class)
    fun `initialize throws ProviderFatalError when evaluationContext is null`() =
        runTest(testDispatcher) {
            val initializer = initializer()
            val ctx: EvaluationContext? = null
            initializer.initialize(ctx)
        }

    @Test
    fun `initialize completes successfully when SDK_READY fires`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkManager = mockk<SdkManager>()
        coEvery { sdkManager.initialize(any(), any(), any(), any()) } returns (factory to client)

        val stateRef = AtomicReference(SplitProviderState())
        val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
        val ctx = ImmutableContext(targetingKey = "user-1")

        val job = async { initializer.initialize(ctx) }
        runCurrent()
        job.await() // should complete without throwing
    }

    @Test
    fun `initialize is idempotent`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkManager = mockk<SdkManager>()
        coEvery { sdkManager.initialize(any(), any(), any(), any()) } returns (factory to client)

        val stateRef = AtomicReference(SplitProviderState())
        val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
        val ctx1 = ImmutableContext(targetingKey = "user-1")
        val ctx2 = ImmutableContext(targetingKey = "user-2")

        initializer.initialize(ctx1)
        initializer.initialize(ctx2)

        coVerify(exactly = 1) { sdkManager.initialize(any(), any(), any(), any()) }
    }

    @Test(expected = CancellationException::class)
    fun `initialize propagates cancellation`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val sdkManager = mockk<SdkManager>()
        coEvery { sdkManager.initialize(any(), any(), any(), any()) } coAnswers {
            delay(10_000)
            factory to client
        }

        val initializer = initializer(sdkManager = sdkManager)
        val ctx = ImmutableContext(targetingKey = "user-1")

        val job = async { initializer.initialize(ctx) }
        runCurrent()
        job.cancel()
        job.await() // should surface CancellationException
    }

    @Test(expected = ProviderNotReadyError::class)
    fun `initialize maps IllegalStateException to ProviderNotReadyError`() =
        runTest(testDispatcher) {
            val sdkManager = mockk<SdkManager>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } throws IllegalStateException("already built")

            val initializer = initializer(sdkManager = sdkManager)
            val ctx = ImmutableContext(targetingKey = "user-1")
            initializer.initialize(ctx)
        }

    @Test
    fun `onContextSet does not do anything if not initialized`() = runTest(testDispatcher) {
        val sdkManager = mockk<SdkManager>()
        val initializer = initializer(sdkManager = sdkManager)
        initializer.onContextSet(null, ImmutableContext(targetingKey = "user-1"))
        coVerify(exactly = 0) { sdkManager.initialize(any(), any(), any(), any()) }
    }

    @Test
    fun `onContextSet does not do anything if new context is equal to old context`() =
        runTest(testDispatcher) {
            val sdkManager = mockk<SdkManager>()
            val factory = mockk<SplitFactory>()
            val client = mockk<SplitClient>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns (factory to client)

            val stateRef = AtomicReference(SplitProviderState())
            val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
            val ctx = ImmutableContext(targetingKey = "user-1")
            initializer.initialize(ctx)
            initializer.onContextSet(ctx, ctx)
            coVerify(exactly = 1) { sdkManager.initialize(any(), any(), any(), any()) }
            coVerify(exactly = 0) { sdkManager.getReadyClient(any(), any(), any()) }
        }

    @Test
    fun `onContextSet fetches a new client if targeting key changes`() = runTest(testDispatcher) {
        val factory = mockk<SplitFactory>()
        val client = mockk<SplitClient>()
        val client2 = mockk<SplitClient>()
        val sdkManager = mockk<SdkManager>()
        coEvery { sdkManager.initialize(any(), any(), "user-1", any()) } returns (factory to client)
        coEvery { sdkManager.getReadyClient(factory, "user-2", any()) } returns client2

        val stateRef = AtomicReference(SplitProviderState())
        val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
        val ctx = ImmutableContext(targetingKey = "user-1")
        initializer.initialize(ctx)
        initializer.onContextSet(ctx, ImmutableContext(targetingKey = "user-2"))
        coVerify(exactly = 1) { sdkManager.initialize(any(), any(), any(), any()) }
        coVerify(exactly = 1) { sdkManager.getReadyClient(factory, "user-2", any()) }
    }

    @Test
    fun `onContextSet does not recreate client when targeting key is unchanged`() =
        runTest(testDispatcher) {
            val factory = mockk<SplitFactory>()
            val client = mockk<SplitClient>()
            val sdkManager = mockk<SdkManager>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    "user-1",
                    any()
                )
            } returns (factory to client)

            val stateRef = AtomicReference(SplitProviderState())
            val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
            val oldCtx = ImmutableContext(targetingKey = "user-1")
            initializer.initialize(oldCtx)

            val newCtxSameKey = ImmutableContext(targetingKey = "user-1")
            initializer.onContextSet(oldCtx, newCtxSameKey)

            coVerify(exactly = 0) { sdkManager.getReadyClient(any(), any(), any()) }
            coVerify(exactly = 1) { sdkManager.initialize(any(), any(), any(), any()) }
        }

    @Test
    fun `onContextSet reuses previously ready client when switching back to a known key`() =
        runTest(testDispatcher) {
            val factory = mockk<SplitFactory>()
            val client1 = mockk<SplitClient>()
            val client2 = mockk<SplitClient>()
            val sdkManager = mockk<SdkManager>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    "user-1",
                    any()
                )
            } returns (factory to client1)
            coEvery { sdkManager.getReadyClient(factory, "user-2", any()) } returns client2
            // If not cached, a naive implementation would call getReadyClient again for user-1.
            // We expect caching to avoid this second call.

            val stateRef = AtomicReference(SplitProviderState())
            val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)

            val key1 = ImmutableContext(targetingKey = "user-1")
            val key2 = ImmutableContext(targetingKey = "user-2")

            initializer.initialize(key1)
            initializer.onContextSet(key1, key2) // instantiate user-2
            initializer.onContextSet(
                key2,
                key1
            ) // switch back to user-1 (should reuse, no new call)

            coVerify(exactly = 1) { sdkManager.initialize(any(), any(), any(), any()) }
            coVerify(exactly = 1) { sdkManager.getReadyClient(factory, "user-2", any()) }
            coVerify(exactly = 0) { sdkManager.getReadyClient(factory, "user-1", any()) }
        }

    @Test
    fun `onContextSet updates evaluationContext when new context is missing targeting key`() =
        runTest(testDispatcher) {
            val factory = mockk<SplitFactory>()
            val client = mockk<SplitClient>()
            val sdkManager = mockk<SdkManager>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    "user-1",
                    any()
                )
            } returns (factory to client)

            val stateRef = AtomicReference(SplitProviderState())
            val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
            val oldCtx = ImmutableContext(targetingKey = "user-1")
            initializer.initialize(oldCtx)

            val newCtxMissingKey: EvaluationContext = ImmutableContext() // missing targeting key
            initializer.onContextSet(oldCtx, newCtxMissingKey)

            coVerify(exactly = 0) { sdkManager.getReadyClient(any(), any(), any()) }
            coVerify(exactly = 1) { sdkManager.initialize(any(), any(), any(), any()) }
        }

    @Test(expected = ProviderNotReadyError::class)
    fun `onContextSet maps IllegalStateException from getReadyClient to ProviderNotReadyError`() =
        runTest(testDispatcher) {
            val factory = mockk<SplitFactory>()
            val client = mockk<SplitClient>()
            val sdkManager = mockk<SdkManager>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    "user-1",
                    any()
                )
            } returns (factory to client)
            coEvery {
                sdkManager.getReadyClient(
                    factory,
                    "user-2",
                    any()
                )
            } throws IllegalStateException("already built")

            val stateRef = AtomicReference(SplitProviderState())
            val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
            val oldCtx = ImmutableContext(targetingKey = "user-1")
            initializer.initialize(oldCtx)

            val newCtx = ImmutableContext(targetingKey = "user-2")
            initializer.onContextSet(oldCtx, newCtx)
        }

    @Test(expected = ProviderFatalError::class)
    fun `onContextSet maps unexpected exceptions from getReadyClient to ProviderFatalError`() =
        runTest(testDispatcher) {
            val factory = mockk<SplitFactory>()
            val client = mockk<SplitClient>()
            val sdkManager = mockk<SdkManager>()
            coEvery {
                sdkManager.initialize(
                    any(),
                    any(),
                    "user-1",
                    any()
                )
            } returns (factory to client)
            coEvery { sdkManager.getReadyClient(factory, "user-2", any()) } throws RuntimeException(
                "boom"
            )

            val stateRef = AtomicReference(SplitProviderState())
            val initializer = initializer(stateRef = stateRef, sdkManager = sdkManager)
            val oldCtx = ImmutableContext(targetingKey = "user-1")
            initializer.initialize(oldCtx)

            val newCtx = ImmutableContext(targetingKey = "user-2")
            initializer.onContextSet(oldCtx, newCtx)
        }

    private fun initializer(
        stateRef: AtomicReference<SplitProviderState> = AtomicReference(SplitProviderState()),
        config: SplitProvider.Config = testConfig(),
        sdkManager: SdkManager = mockk(),
        defaultReadyTimeoutMs: Long = 10_000L,
    ): Initializer {
        return Initializer(
            stateRef = stateRef,
            config = config,
            sdkManager = sdkManager,
            defaultReadyTimeoutMs = defaultReadyTimeoutMs
        )
    }

    private fun testConfig(): SplitProvider.Config =
        SplitProvider.Config(
            applicationContext = ApplicationProvider.getApplicationContext(),
            sdkKey = "test-api-key"
        )
}
