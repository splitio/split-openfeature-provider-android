package io.harness.openfeature.split

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.EvaluationMetadata
import dev.openfeature.sdk.FeatureProvider
import dev.openfeature.sdk.Hook
import dev.openfeature.sdk.ProviderEvaluation
import dev.openfeature.sdk.ProviderMetadata
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.ErrorCode
import io.split.android.client.SplitClient
import io.split.android.client.SplitClientConfig
import io.split.android.client.SplitFactory
import io.split.android.client.SplitFactoryBuilder
import io.split.android.client.SplitResult
import io.split.android.client.api.Key

internal const val PROVIDER_NAME = "SPLIT_PROVIDER"

class SplitProvider(
    override val hooks: List<Hook<*>>,
    override val metadata: ProviderMetadata,
    val apiKey: String,
    val config: SplitClientConfig,
    val context: Context
) : FeatureProvider {

    private lateinit var splitClient: SplitClient

    companion object {
        private class SplitProviderMetadata(override val name: String? = PROVIDER_NAME) :
            ProviderMetadata

        fun create(
            hooks: List<Hook<*>> = listOf(),
            metadata: ProviderMetadata = SplitProviderMetadata(),
            apiKey: String,
            config: SplitClientConfig = SplitClientConfig.builder().build(),
            context: Context
        ): SplitProvider {
            return SplitProvider(
                hooks = hooks,
                metadata = metadata,
                apiKey = apiKey,
                config = config,
                context = context
            )
        }
    }

    private fun <T> evaluateFlag(flagName: String?, defaultValue: T): ProviderEvaluation<T> {
        val gson = Gson()

        val evaluationMetadataBuilder = EvaluationMetadata.builder()

        val evaluatedResult: SplitResult = splitClient.getTreatmentWithConfig(flagName, mapOf())

        if (evaluatedResult.treatment() == "" || evaluatedResult.treatment() == "control") {
            return ProviderEvaluation<T>(
                value = defaultValue,
                reason = Reason.DEFAULT.name,
            )
        }

        // Handle different types of values based on defaultValue type
        @Suppress("UNCHECKED_CAST") val result = when (defaultValue) {
            is Boolean -> {
                // For boolean flags, "on" typically means true
                var treatment = defaultValue as Boolean
                treatment =
                    if (evaluatedResult.treatment() == "on" || evaluatedResult.treatment() == "true") {
                        true
                    } else if (evaluatedResult.treatment() == "off" || evaluatedResult.treatment() == "false") {
                        false
                    } else {
                        return ProviderEvaluation<T>(
                            value = defaultValue,
                            reason = Reason.ERROR.name,
                            errorCode = ErrorCode.TYPE_MISMATCH,
                            errorMessage = "Treatment ${evaluatedResult.treatment()} is not boolean",
                        )
                    }
                treatment as T
            }

            is String -> {
                evaluatedResult.treatment() as T
            }

            is Double -> {
                val treatmentToDouble = evaluatedResult.treatment().toDoubleOrNull()
                if (treatmentToDouble == null) {
                    return ProviderEvaluation<T>(
                        value = defaultValue,
                        reason = Reason.ERROR.name,
                        errorCode = ErrorCode.TYPE_MISMATCH,
                        errorMessage = "Treatment ${evaluatedResult.treatment()} is not a valid double",
                    )
                }
                treatmentToDouble as T
            }

            is Int -> {
                val treatmentToInt = evaluatedResult.treatment().toIntOrNull()
                if (treatmentToInt == null) {
                    return ProviderEvaluation<T>(
                        value = defaultValue,
                        reason = Reason.ERROR.name,
                        errorCode = ErrorCode.TYPE_MISMATCH,
                        errorMessage = "Treatment ${evaluatedResult.treatment()} is not a valid integer",
                    )
                }
                treatmentToInt as T
            }

            is Value -> {
                // Parse the JSON string to appropriate Value type
                try {
                    // First try to parse as a JSON object (Structure)
                    parseJsonToValue<T>(evaluatedResult, gson)
                } catch (e: Exception) {
                    return ProviderEvaluation<T>(
                        value = Value.String(evaluatedResult.treatment()) as T,
                        reason = Reason.ERROR.name,
                        errorCode = ErrorCode.PARSE_ERROR,
                        errorMessage = "Failed to parse treatment to Value: ${e.message}",
                    )
                }
            }

            else -> {
                // For other types, fall back to default
                return ProviderEvaluation<T>(
                    value = defaultValue,
                    reason = Reason.ERROR.name,
                    errorCode = ErrorCode.GENERAL,
                    errorMessage = "Unsupported type ${defaultValue!!::class.simpleName}",
                )
            }
        }

        if (evaluatedResult.config() != null) {
            evaluationMetadataBuilder.putString("config", evaluatedResult.config())
        }

        return ProviderEvaluation<T>(
            value = result,
            reason = Reason.TARGETING_MATCH.name,
            metadata = evaluationMetadataBuilder.build()
        )
    }


    @Suppress("UNCHECKED_CAST")
    private fun <T> parseJsonToValue(
        evaluatedResult: SplitResult, gson: Gson
    ): T = if (evaluatedResult.treatment().trim().startsWith("{")) {
        val jsonMap = gson.fromJson<Map<String, Any>>(
            evaluatedResult.treatment(), object : TypeToken<Map<String, Any>>() {}.type
        )

        // Convert the parsed map to OpenFeature Value.Structure
        val valueMap = jsonMap.mapValues { (_, value) ->
            convertToValue(value)
        }

        Value.Structure(valueMap) as T
    }
    // Try to parse as a JSON array (List)
    else if (evaluatedResult.treatment().trim().startsWith("[")) {
        val jsonList = gson.fromJson<List<Any>>(
            evaluatedResult.treatment(), object : TypeToken<List<Any>>() {}.type
        )

        // Convert the parsed list to OpenFeature Value.List
        val valueList = jsonList.map { convertToValue(it) }

        Value.List(valueList) as T
    }
    // Try to parse as primitive types
    else {
        val treatmentStr = evaluatedResult.treatment()

        // Try to parse as boolean
        if (treatmentStr.equals("true", ignoreCase = true)) {
            Value.Boolean(true) as T
        } else if (treatmentStr.equals("false", ignoreCase = true)) {
            Value.Boolean(false) as T
        }
        // Try to parse as number
        else if (treatmentStr.toIntOrNull() != null) {
            Value.Integer(treatmentStr.toInt()) as T
        } else if (treatmentStr.toDoubleOrNull() != null) {
            Value.Double(treatmentStr.toDouble()) as T
        }
        // Default to string
        else {
            Value.String(treatmentStr) as T
        }
    }

    // Helper function to convert Any to appropriate Value type
    private fun convertToValue(value: Any?): Value {
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

    override fun getBooleanEvaluation(
        key: String, defaultValue: Boolean, context: EvaluationContext?
    ): ProviderEvaluation<Boolean> {
        return evaluateFlag(key, defaultValue)
    }

    override fun getDoubleEvaluation(
        key: String, defaultValue: Double, context: EvaluationContext?
    ): ProviderEvaluation<Double> {
        return evaluateFlag(key, defaultValue)
    }

    override fun getIntegerEvaluation(
        key: String, defaultValue: Int, context: EvaluationContext?
    ): ProviderEvaluation<Int> {
        return evaluateFlag(key, defaultValue)
    }

    override fun getObjectEvaluation(
        key: String, defaultValue: Value, context: EvaluationContext?
    ): ProviderEvaluation<Value> {
        return evaluateFlag(key, defaultValue)
    }

    override fun getStringEvaluation(
        key: String, defaultValue: String, context: EvaluationContext?
    ): ProviderEvaluation<String> {
        return evaluateFlag(key, defaultValue)
    }

    override suspend fun initialize(initialContext: EvaluationContext?) {
        // Get targeting key from context or use a default one if not available
        checkNotNull(initialContext?.getTargetingKey())

        val targetingKey = initialContext.getTargetingKey()
        val key = Key(targetingKey)

        try {
            // Create factory
            val splitFactory: SplitFactory = SplitFactoryBuilder.build(apiKey, key, config, context)

            // Initialize the split client
            splitClient = splitFactory.client()

        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Split client", e)
        }
    }

    override suspend fun onContextSet(
        oldContext: EvaluationContext?, newContext: EvaluationContext
    ) {
        // Re-init the splitClient?
    }

    override fun shutdown() {
        splitClient.flush()
        splitClient.destroy()
    }

}
