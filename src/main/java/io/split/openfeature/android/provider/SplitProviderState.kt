package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.EvaluationContext
import io.split.android.client.SplitClient
import io.split.android.client.SplitFactory

/**
 * Internal state holder for SplitProvider.
 */
internal data class SplitProviderState(
    val initialized: Boolean = false,
    val defaultContext: EvaluationContext? = null,
    val splitFactory: SplitFactory? = null,
    val splitClient: SplitClient? = null,
)
