package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.EvaluationMetadata
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.GeneralError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ParseError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.ProviderNotReadyError
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError.TargetingKeyMissingError
import io.split.android.client.SplitClient
import io.split.android.client.SplitResult
import java.util.concurrent.atomic.AtomicReference


internal interface EvaluatorDelegate {

    fun getBooleanEvaluation(
        key: String, defaultValue: Boolean, context: EvaluationContext?
    ): ProviderEvaluation<Boolean>

    fun getStringEvaluation(
        key: String, defaultValue: String, context: EvaluationContext?
    ): ProviderEvaluation<String>

    fun getIntegerEvaluation(
        key: String, defaultValue: Int, context: EvaluationContext?
    ): ProviderEvaluation<Int>

    fun getDoubleEvaluation(
        key: String, defaultValue: Double, context: EvaluationContext?
    ): ProviderEvaluation<Double>

    fun getObjectEvaluation(
        key: String, defaultValue: Value, context: EvaluationContext?
    ): ProviderEvaluation<Value>
}

internal class DefaultEvaluator(
    private val state: AtomicReference<SplitProviderState>,
    private val serialization: Serialization = DefaultSerialization()
) : EvaluatorDelegate {

    override fun getBooleanEvaluation(
        key: String, defaultValue: Boolean, context: EvaluationContext?
    ): ProviderEvaluation<Boolean> = evaluateCommon(
        key = key,
        context = context,
        mapper = booleanMapper,
        errorMessage = "Error getting boolean evaluation"
    )

    override fun getStringEvaluation(
        key: String, defaultValue: String, context: EvaluationContext?
    ): ProviderEvaluation<String> = evaluateCommon(
        key = key,
        context = context,
        mapper = stringMapper,
        errorMessage = "Error getting String evaluation"
    )

    override fun getIntegerEvaluation(
        key: String, defaultValue: Int, context: EvaluationContext?
    ): ProviderEvaluation<Int> = evaluateCommon(
        key = key,
        context = context,
        mapper = intMapper,
        errorMessage = "Error getting String evaluation"
    )

    override fun getDoubleEvaluation(
        key: String, defaultValue: Double, context: EvaluationContext?
    ): ProviderEvaluation<Double> = evaluateCommon(
        key = key,
        context = context,
        mapper = doubleMapper,
        errorMessage = "Error getting String evaluation"
    )

    override fun getObjectEvaluation(
        key: String, defaultValue: Value, context: EvaluationContext?
    ): ProviderEvaluation<Value> = evaluateCommon(
        key = key,
        context = context,
        mapper = objectMapper,
        errorMessage = "Error getting object evaluation"
    )

    private fun <T> evaluateCommon(
        key: String,
        context: EvaluationContext?,
        mapper: (treatment: String, evaluated: SplitResult) -> T,
        errorMessage: String
    ): ProviderEvaluation<T> {
        val (evalContext, client) = getContextAndSplitClient(context)
        return try {
            val evaluated: SplitResult = evaluateTreatment(client, key, evalContext)
            val treatment = evaluated.treatment()
            val mapped = mapper(treatment, evaluated)
            val config = evaluated.config()
            if (!config.isNullOrBlank()) {
                ProviderEvaluation(
                    value = mapped,
                    variant = treatment,
                    metadata = EvaluationMetadata.builder().putString("config", config).build()
                )
            } else {
                ProviderEvaluation(value = mapped, variant = treatment)
            }
        } catch (e: OpenFeatureError) {
            throw e
        } catch (e: Exception) {
            throw GeneralError("$errorMessage: ${e.message}")
        }
    }

    private fun evaluateTreatment(
        client: SplitClient, key: String, evaluationContext: EvaluationContext?
    ): SplitResult {
        val attributes: Map<String, Any?> = evaluationContext?.asObjectMap() ?: emptyMap()
        return client.getTreatmentWithConfig(key, attributes)
    }

    private fun getContextAndSplitClient(context: EvaluationContext?): Pair<EvaluationContext, SplitClient> {
        // Get current state
        val currentState = state.get()

        // Define evaluation context
        val evalContext = context ?: currentState.defaultContext
            ?: throw TargetingKeyMissingError("Targeting key missing in evaluation context")

        val requestedKey = evalContext.getTargetingKey()

        if (requestedKey.isBlank()) {
            throw TargetingKeyMissingError("Targeting key missing in evaluation context")
        }

        val client = currentState.splitClient ?: throw ProviderNotReadyError()

        // Ensure the active client matches the requested key. To use a different key, caller must setContext first.
        if (currentState.activeKey != requestedKey) {
            throw ProviderNotReadyError("Requested targetingKey ('$requestedKey') differs from active key ('${currentState.activeKey}'). Call setContext first to switch.")
        }

        return Pair(evalContext, client)
    }

    private val booleanMapper: (treatment: String, evaluated: SplitResult) -> Boolean =
        { treatment, _ ->
            when (treatment.lowercase()) {
                "on", "true" -> true
                "off", "false" -> false
                else -> throw ParseError("Error getting boolean evaluation")
            }
        }
    private val stringMapper: (treatment: String, evaluated: SplitResult) -> String =
        { treatment, _ -> treatment }
    private val intMapper: (treatment: String, evaluated: SplitResult) -> Int = { treatment, _ ->
        runCatching { treatment.toInt() }.getOrElse { throw ParseError("Error getting int evaluation") }
    }
    private val doubleMapper: (treatment: String, evaluated: SplitResult) -> Double =
        { treatment, _ ->
            runCatching { treatment.toDouble() }.getOrElse { throw ParseError("Error getting double evaluation") }
        }
    private val objectMapper: (treatment: String, evaluated: SplitResult) -> Value =
        { treatment, _ ->
            if (treatment.isBlank()) throw ParseError("Error getting object evaluation")
            try {
                serialization.deserializeToValue(treatment)
            } catch (_: Exception) {
                throw ParseError("Error getting object evaluation")
            }
        }
}
