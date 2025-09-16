package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.FeatureProvider
import org.junit.Test

import org.junit.Assert.*

class SplitProviderTest {

    @Test
    fun `name is Split`() {
        assertEquals("Split", SplitProvider().metadata.name)
    }

    @Test
    fun `SplitProvider implements FeatureProvider`() {
        assertTrue(SplitProvider() is FeatureProvider)
    }
}
