package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.FeatureProvider
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class SplitProviderTest {

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

    private fun getProvider(): SplitProvider = SplitProvider(config = testConfig())

    private fun testConfig(): SplitProvider.Config =
        SplitProvider.Config(
            applicationContext = ApplicationProvider.getApplicationContext(),
            apiKey = "test-api-key"
        )
}
