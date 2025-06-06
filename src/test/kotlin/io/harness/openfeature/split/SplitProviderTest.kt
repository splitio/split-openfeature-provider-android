package io.harness.openfeature.split


import dev.openfeature.sdk.EvaluationMetadata
import dev.openfeature.sdk.Reason
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.exceptions.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.split.android.client.SplitClient
import io.split.android.client.SplitResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SplitProviderTest {

    // Mock objects
    private lateinit var splitClient: SplitClient
    private lateinit var provider: SplitProvider


    @BeforeEach
    fun setUp() {
        // Create a mock Split client
        splitClient = mockk<SplitClient>(relaxed = true)

        // Create the provider with the necessary dependencies mocked
        provider = SplitProvider(
            hooks = listOf(),
            metadata = mockk {
                every { name } returns "SPLIT_PROVIDER"
            },
            apiKey = "test-api-key",
            config = mockk(relaxed = true),
            context = mockk(relaxed = true)
        )

        // Set the mocked Split client in the provider
        val field = SplitProvider::class.java.getDeclaredField("splitClient")
        field.isAccessible = true
        field.set(provider, splitClient)
    }

    @Test
    fun `getBooleanEvaluation should return true when treatment is true`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "true"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("boolean_flag_on", false, null)

        // Assert
        assertEquals(true, result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getBooleanEvaluation should return false when treatment is false`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "true"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("boolean_flag_on", false, null)

        // Assert
        assertEquals(true, result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getBooleanEvaluation should return true when treatment is on`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "on"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("boolean_flag_on", false, null)

        // Assert
        assertEquals(true, result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getBooleanEvaluation should return false when treatment is off`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "off"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("boolean_flag_off", true, null)

        // Assert
        assertEquals(false, result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getBooleanEvaluation should return default value when treatment is control`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "control"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("unknown_flag", true, null)

        // Assert
        assertEquals(true, result.value)
        assertEquals(Reason.DEFAULT.name, result.reason)
    }

    @Test
    fun `getBooleanEvaluation should return default value when treatment is empty`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns ""
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("unknown_flag", false, null)

        // Assert
        assertEquals(false, result.value)
        assertEquals(Reason.DEFAULT.name, result.reason)
    }

    @Test
    fun `getBooleanEvaluation should return default value when treatment parsing error`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "not_boolean_value"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getBooleanEvaluation("unknown_flag", false, null)

        // Assert
        assertEquals(Reason.ERROR.name, result.reason)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals("Treatment not_boolean_value is not boolean", result.errorMessage)
        assertEquals(false, result.value)
    }

    @Test
    fun `getStringEvaluation should return treatment value as string`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "value1"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getStringEvaluation("string_flag", "default", null)

        // Assert
        assertEquals("value1", result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getIntegerEvaluation should return treatment value as integer`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "42"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getIntegerEvaluation("number_flag", 0, null)

        // Assert
        // Assert
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
        assertEquals(42, result.value)

    }

    @Test
    fun `getIntegerEvaluation should return default treatment when non-integer string`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "abc"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getIntegerEvaluation("number_flag", 0, null)

        // Assert
        assertEquals(Reason.ERROR.name, result.reason)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals("Treatment abc is not a valid integer", result.errorMessage)
        assertEquals(0, result.value)
    }

    @Test
    fun `getIntegerEvaluation should return default treatment when decimal string`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "0.1"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getIntegerEvaluation("number_flag", 0, null)

        // Assert
        assertEquals(Reason.ERROR.name, result.reason)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals("Treatment 0.1 is not a valid integer", result.errorMessage)
        assertEquals(0, result.value)
    }

    @Test
    fun `getDoubleEvaluation should return treatment value as double`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "3.14"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getDoubleEvaluation("double_flag", 0.0, null)

        // Assert
        assertEquals(3.14, result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getDoubleEvaluation should return treatment value as double for whole number`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "3"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getDoubleEvaluation("double_flag", 0.0, null)

        // Assert
        assertEquals(3.0, result.value)
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
    }

    @Test
    fun `getDoubleEvaluation should return default treatment when non-numeric string`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "abc"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getDoubleEvaluation("number_flag", 0.0, null)

        // Assert
        assertEquals(Reason.ERROR.name, result.reason)
        assertEquals(ErrorCode.TYPE_MISMATCH, result.errorCode)
        assertEquals("Treatment abc is not a valid double", result.errorMessage)
        assertEquals(0.0, result.value)
    }


    @Test
    fun `getObjectEvaluation should return valid object`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "{\"string_value\":\"value\",\"nested\":{\"key\":\"value\"},\"numeric_value\":1,\"boolean_value\":true}"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getObjectEvaluation("object_flag", Value.Null, null)

        // Assert
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
        assertEquals(
            Value.Structure(
                mapOf(
                    "string_value" to Value.String("value"),
                    "nested" to Value.Structure(mapOf("key" to Value.String("value"))),
                    "numeric_value" to Value.Double(1.0),
                    "boolean_value" to Value.Boolean(true)
                )
            ), result.value
        )
    }

    @Test
    fun `getObjectEvaluation should return valid array`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "[{\"string_value\":\"value\",\"nested\":{\"key\":\"value\"},\"numeric_value\":1,\"boolean_value\":true}]"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getObjectEvaluation("object_flag", Value.Null, null)

        // Assert
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
        assertEquals(
            Value.List(
                listOf(
                    Value.Structure(
                        mapOf(
                            "string_value" to Value.String("value"),
                            "nested" to Value.Structure(mapOf("key" to Value.String("value"))),
                            "numeric_value" to Value.Double(1.0),
                            "boolean_value" to Value.Boolean(true)
                        )
                    )
                )
            ), result.value
        )
    }

    @Test
    fun `getObjectEvaluation should return valid primitive`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "primitive"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult

        // Act
        val result = provider.getObjectEvaluation("object_flag", Value.Null, null)

        // Assert
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
        assertEquals(Value.String("primitive"), result.value)
    }

    @Test
    fun `getObjectEvaluation should return error on invalid json`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "{\"key\":"
        every { mockResult.config() } returns null
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult


        // Act
        val result = provider.getObjectEvaluation("object_flag", Value.String(""), null)

        // Assert
        assertEquals(Reason.ERROR.name, result.reason)
        assertEquals(Value.String("{\"key\":") as Value, result.value)
    }

    @Test
    fun `evaluation with treatment config`() {
        // Arrange
        val mockResult = mockk<SplitResult>()
        every { mockResult.treatment() } returns "true"
        every { mockResult.config() } returns "abc"
        every { splitClient.getTreatmentWithConfig(any(), any()) } returns mockResult


        // Act
        val result = provider.getBooleanEvaluation("object_flag", false, null)

        // Assert
        assertEquals(Reason.TARGETING_MATCH.name, result.reason)
        assertEquals(true, result.value)
        val expectedMetadata = EvaluationMetadata.builder().putString("k1", "string_value").build()
        assert(expectedMetadata == result.metadata)
    }


}
