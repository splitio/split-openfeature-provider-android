package io.harness.openfeature.split

import android.content.Context
import com.google.gson.Gson
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

    // Error handling is now in SplitProviderUtils

    /**
     * Evaluates a feature flag with the given name and returns a typed result
     */
    private fun <T> evaluateFlag(flagName: String?, defaultValue: T): ProviderEvaluation<T> {
        val gson = Gson()
        val evaluationMetadataBuilder = EvaluationMetadata.builder()
        val evaluatedResult: SplitResult = splitClient.getTreatmentWithConfig(flagName, mapOf())
        val treatment = evaluatedResult.treatment()

        // Return default value if treatment is empty or control
        if (treatment.isEmpty() || treatment == "control") {
            return ProviderEvaluation(
                value = defaultValue,
                reason = Reason.DEFAULT.name
            )
        }

        // Handle different types of values based on defaultValue type
        @Suppress("UNCHECKED_CAST")
        val result = try {
            when (defaultValue) {
                is Boolean -> handleBooleanTreatment(treatment, defaultValue)
                is String -> treatment as T
                is Double -> handleDoubleTreatment(treatment, defaultValue)
                is Int -> handleIntTreatment(treatment, defaultValue)
                is Value -> handleValueTreatment(evaluatedResult, gson)
                else -> return SplitProviderUtils.createErrorEvaluation(
                    defaultValue,
                    ErrorCode.GENERAL,
                    "Unsupported type ${defaultValue!!::class.simpleName}"
                )
            }
        } catch (e: TypeCastException) {
            return SplitProviderUtils.createErrorEvaluation(
                defaultValue,
                ErrorCode.TYPE_MISMATCH,
                "Type mismatch for treatment: $treatment"
            )
        }

        // Add config to metadata if available
        evaluatedResult.config()?.let {
            evaluationMetadataBuilder.putString("config", it)
        }

        return ProviderEvaluation(
            value = result,
            reason = Reason.TARGETING_MATCH.name,
            metadata = evaluationMetadataBuilder.build()
        )
    }

    /**
     * Handles boolean treatment conversion with special cases for on/off values
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> handleBooleanTreatment(treatment: String, defaultValue: T): T {
        return SplitProviderUtils.handleBooleanTreatment(treatment) as T
    }

    /**
     * Handles double treatment conversion
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> handleDoubleTreatment(treatment: String, defaultValue: T): T {
        return SplitProviderUtils.handleDoubleTreatment(treatment) as T
    }

    /**
     * Handles integer treatment conversion
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> handleIntTreatment(treatment: String, defaultValue: T): T {
        return SplitProviderUtils.handleIntTreatment(treatment) as T
    }

    /**
     * Handles Value treatment conversion using JSON parsing
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> handleValueTreatment(evaluatedResult: SplitResult, gson: Gson): T {
        try {
            return SplitProviderUtils.parseJsonToValue(evaluatedResult, gson) as T
        } catch (e: Exception) {
            throw TypeCastException("Failed to parse treatment to Value: ${e.message}")
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

    /**
     * Initializes the Split client with the initial evaluation context
     * @throws IllegalArgumentException if the targeting key is missing
     * @throws RuntimeException if initialization fails
     */
    override suspend fun initialize(initialContext: EvaluationContext?) {
        // Validate context has a targeting key
        val targetingKey = initialContext?.getTargetingKey()
            ?: throw IllegalArgumentException("Targeting key is required in the evaluation context")

        initializeSplitClient(targetingKey)
    }

    /**
     * Helper method to initialize the Split client with a targeting key
     * @throws RuntimeException if initialization fails
     */
    private fun initializeSplitClient(targetingKey: String) {
        val key = Key(targetingKey)

        try {
            // Create factory and initialize client
            val splitFactory = SplitFactoryBuilder.build(apiKey, key, config, context)
            splitClient = splitFactory.client()
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Split client", e)
        }
    }

    /**
     * Reinitializes the Split client when the evaluation context changes
     */
    override suspend fun onContextSet(
        oldContext: EvaluationContext?, newContext: EvaluationContext
    ) {
        val newTargetingKey = newContext.getTargetingKey()

        // Check if targeting key has changed
        if (oldContext?.getTargetingKey() != newTargetingKey) {
            // Clean up old client
            if (::splitClient.isInitialized) {
                splitClient.flush()
                splitClient.destroy()
            }

            // Initialize with new context
            initializeSplitClient(newTargetingKey)
        }
    }

    override fun shutdown() {
        splitClient.flush()
        splitClient.destroy()
    }

}
