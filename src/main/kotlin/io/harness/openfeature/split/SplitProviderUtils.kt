package io.harness.openfeature.split

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.ErrorCode
import io.split.android.client.SplitResult

/**
 * Utility class for Split Provider operations
 */
object SplitProviderUtils {

    /**
     * Creates a type-specific error evaluation with appropriate error details
     */
    fun <T> createErrorEvaluation(
        defaultValue: T,
        errorCode: ErrorCode,
        errorMessage: String
    ): ProviderEvaluation<T> {
        return ProviderEvaluation(
            value = defaultValue,
            reason = Reason.ERROR.name,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    /**
     * Parses a JSON string to an appropriate Value type
     */
    fun parseJsonToValue(evaluatedResult: SplitResult, gson: Gson): Value {
        val treatment = evaluatedResult.treatment().trim()

        return when {
            treatment.startsWith("{") -> parseJsonObject(treatment, gson)
            treatment.startsWith("[") -> parseJsonArray(treatment, gson)
            else -> parsePrimitive(treatment)
        }
    }

    /**
     * Parses a JSON object string into a Value.Structure
     */
    private fun parseJsonObject(jsonString: String, gson: Gson): Value {
        val jsonMap = gson.fromJson<Map<String, Any>>(
            jsonString, object : TypeToken<Map<String, Any>>() {}.type
        )

        val valueMap = jsonMap.mapValues { (_, value) -> convertToValue(value) }
        return Value.Structure(valueMap)
    }

    /**
     * Parses a JSON array string into a Value.List
     */
    private fun parseJsonArray(jsonString: String, gson: Gson): Value {
        val jsonList = gson.fromJson<List<Any>>(
            jsonString, object : TypeToken<List<Any>>() {}.type
        )

        val valueList = jsonList.map { convertToValue(it) }
        return Value.List(valueList)
    }

    /**
     * Parses a primitive value string into the appropriate Value type
     */
    fun parsePrimitive(valueString: String): Value {
        return when {
            // Boolean values
            valueString.equals("true", ignoreCase = true) -> Value.Boolean(true)
            valueString.equals("false", ignoreCase = true) -> Value.Boolean(false)

            // Number values - try integer first, then double
            valueString.toIntOrNull() != null -> Value.Integer(valueString.toInt())
            valueString.toDoubleOrNull() != null -> Value.Double(valueString.toDouble())

            // Default to string for everything else
            else -> Value.String(valueString)
        }
    }

    /**
     * Helper function to convert Any to appropriate Value type
     */
    fun convertToValue(value: Any?): Value {
        return when (value) {
            null -> Value.Null
            is String -> Value.String(value)
            is Boolean -> Value.Boolean(value)
            is Int -> Value.Integer(value)
            is Double -> Value.Double(value)
            is Float -> Value.Double(value.toDouble())
            is Long -> Value.Integer(value.toInt())
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST") val map = value as Map<String, Any?>
                Value.Structure(map.mapValues { (_, v) -> convertToValue(v) })
            }

            is List<*> -> Value.List(value.map { convertToValue(it) })
            else -> Value.String(value.toString())
        }
    }

    /**
     * Handles boolean treatment conversion with special cases for on/off values
     */
    fun handleBooleanTreatment(treatment: String): Boolean {
        return when {
            treatment.equals("on", ignoreCase = true) ||
                    treatment.equals("true", ignoreCase = true) -> true

            treatment.equals("off", ignoreCase = true) ||
                    treatment.equals("false", ignoreCase = true) -> false

            else -> throw TypeCastException("Treatment $treatment is not boolean")
        }
    }

    /**
     * Handles double treatment conversion
     */
    fun handleDoubleTreatment(treatment: String): Double {
        return treatment.toDoubleOrNull()
            ?: throw TypeCastException("Treatment $treatment is not a valid double")
    }

    /**
     * Handles integer treatment conversion
     */
    fun handleIntTreatment(treatment: String): Int {
        return treatment.toIntOrNull()
            ?: throw TypeCastException("Treatment $treatment is not a valid integer")
    }
}
