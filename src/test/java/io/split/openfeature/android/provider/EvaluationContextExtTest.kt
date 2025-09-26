package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value
import io.split.openfeature.android.provider.EvaluationContextExt.getTrafficType
import io.split.openfeature.android.provider.EvaluationContextExt.withTrafficType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationContextExtTest {

    @Test
    fun `default trafficType is null`() {
        val ctx = ImmutableContext()

        assertNull(ctx.getTrafficType())
    }

    @Test
    fun `withTrafficType sets trafficType value`() {
        val ctx = ImmutableContext()
        val newCtx = ctx.withTrafficType("user")

        assertEquals("user", newCtx.getTrafficType())
    }

    @Test
    fun `withTrafficType does not override other values`() {
        val ctx = ImmutableContext("user-id", attributes = mapOf("MyAttr" to Value.Boolean(true)))
        val newCtx = ctx.withTrafficType("account")
        assertEquals("account", newCtx.getTrafficType())
        assertEquals(true, newCtx.getValue("MyAttr")?.asBoolean())
        assertEquals("user-id", newCtx.getTargetingKey())
    }
}
