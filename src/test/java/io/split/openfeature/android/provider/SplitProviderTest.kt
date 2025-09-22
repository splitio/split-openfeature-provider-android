package io.split.openfeature.android.provider

import androidx.test.core.app.ApplicationProvider
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.ImmutableContext
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class SplitProviderTest : BaseMockkTest() {

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
    fun `initialize delegates to initializer`() = runTest(StandardTestDispatcher()) {
        val initializer = mockk<Initializer>(relaxed = true)
        val provider = SplitProvider(
            config = testConfig(),
            dispatcher = StandardTestDispatcher(),
            initializer = initializer
        )
        val ctx: EvaluationContext = ImmutableContext(targetingKey = "user-1")

        provider.initialize(ctx)

        coVerify(exactly = 1) { initializer.initialize(ctx) }
    }

    @Test
    fun `onContextSet delegates to initializer`() = runTest(StandardTestDispatcher()) {
        val initializer = mockk<Initializer>(relaxed = true)
        val provider = SplitProvider(
            config = testConfig(),
            dispatcher = StandardTestDispatcher(),
            initializer = initializer
        )
        val oldCtx = ImmutableContext(targetingKey = "old")
        val newCtx = ImmutableContext(targetingKey = "new")

        provider.onContextSet(oldCtx, newCtx)

        coVerify(exactly = 1) { initializer.onContextSet(oldCtx, newCtx) }
    }

    @Test
    fun `shutdown delegates to initializer`() {
        val initializer = mockk<Initializer>(relaxed = true)
        val provider = SplitProvider(
            config = testConfig(),
            initializer = initializer
        )

        provider.shutdown()

        verify(exactly = 1) { initializer.shutdown() }
    }

    private fun getProvider(): SplitProvider = SplitProvider(config = testConfig())

    private fun testConfig(): SplitProvider.Config =
        SplitProvider.Config(
            applicationContext = ApplicationProvider.getApplicationContext(),
            sdkKey = "test-api-key"
        )
}
