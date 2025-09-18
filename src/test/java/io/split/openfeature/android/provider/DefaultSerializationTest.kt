package io.split.openfeature.android.provider

import dev.openfeature.kotlin.sdk.Value
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DefaultSerializationTest {

    private val serialization = DefaultSerialization()

    @Test
    fun `deserialize JSON object`() {
        val input = """
            {
              "a": 1,
              "b": "x",
              "c": true,
              "n": null,
              "nested": {
                "d": [1, {"e": "y"}],
                "f": {"g": 2.5}
              }
            }
        """.trimIndent()

        val value = serialization.deserializeToValue(input)
        val expected = Value.Structure(
            mapOf(
                "a" to Value.Integer(1),
                "b" to Value.String("x"),
                "c" to Value.Boolean(true),
                "n" to Value.Null,
                "nested" to Value.Structure(
                    mapOf(
                        "d" to Value.List(
                            listOf(
                                Value.Integer(1),
                                Value.Structure(mapOf("e" to Value.String("y")))
                            )
                        ),
                        "f" to Value.Structure(
                            mapOf(
                                "g" to Value.Double(2.5)
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expected, value)
    }

    @Test
    fun `deserialize JSON array`() {
        val input = "[1, 2, 3]"
        val value = serialization.deserializeToValue(input)
        val expected = Value.List(listOf(Value.Integer(1), Value.Integer(2), Value.Integer(3)))
        assertEquals(expected, value)
    }

    @Test
    fun `deserialize JSON string`() {
        val input = "\"hello\""
        val value = serialization.deserializeToValue(input)
        assertEquals(Value.String("hello"), value)
    }

    @Test
    fun `deserialize JSON number`() {
        val input = "1234"
        val value = serialization.deserializeToValue(input)
        assertEquals(Value.Integer(1234), value)
    }

    @Test
    fun `deserialize JSON boolean`() {
        val input = "true"
        val value = serialization.deserializeToValue(input)
        assertEquals(Value.Boolean(true), value)
    }

    @Test(expected = SerializationException::class)
    fun `invalid JSON throws`() {
        val input = "{invalid-json}"
        serialization.deserializeToValue(input)
        fail("Expected SerializationException")
    }
}
