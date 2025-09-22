package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.Value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

internal interface Serialization {
    fun deserializeToValue(value: String): Value
}

internal class DefaultSerialization : Serialization {
    override fun deserializeToValue(value: String): Value {
        return Json.parseToJsonElement(value).toValue()
    }

    fun JsonElement.toValue(): Value = when (this) {
        is JsonNull -> Value.Null
        is JsonPrimitive -> this.toValue()
        is JsonObject -> Value.Structure(
            buildMap(this.size) {
                for ((k, v) in this@toValue) put(k, v.toValue())
            }
        )

        is JsonArray -> Value.List(
            buildList(this.size) {
                for (el in this@toValue) add(el.toValue())
            }
        )
    }

    fun JsonPrimitive.toValue(): Value {
        if (isString) return Value.String(contentOrNull ?: "")
        booleanOrNull?.let { return Value.Boolean(it) }

        val asDouble = doubleOrNull ?: return Value.Null
        // If the double is an integer value within Int range, prefer Integer
        val asIntCandidate = asDouble.toInt()
        return if (asIntCandidate.toDouble() == asDouble &&
            asDouble <= Int.MAX_VALUE.toDouble() && asDouble >= Int.MIN_VALUE.toDouble()
        ) {
            Value.Integer(asIntCandidate)
        } else {
            Value.Double(asDouble)
        }
    }
}
