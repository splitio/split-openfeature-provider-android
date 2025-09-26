package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.Value

internal object EvaluationContextExt {
    internal fun EvaluationContext.getTrafficType(): String? {
        return getValue("trafficType")?.asString()
    }

    fun EvaluationContext.withTrafficType(trafficType: String): EvaluationContext {
        return ImmutableContext(
            targetingKey = getTargetingKey(),
            attributes = this.asMap() + mapOf("trafficType" to Value.String(trafficType.trim()))
        )
    }
}
