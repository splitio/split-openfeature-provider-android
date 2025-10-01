package io.split.openfeature.android.provider

import io.mockk.mockk
import io.mockk.unmockkAll
import io.split.android.client.SplitClient
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * A SplitClient test double that captures event subscriptions and allows firing them.
 */
class TestHelperClient : SplitClient by mockk(relaxed = true) {
    private val listeners = mutableMapOf<SplitEvent, MutableList<(SplitClient?) -> Unit>>()

    override fun on(event: SplitEvent?, task: SplitEventTask?) {
        if (event != null && task != null) {
            listeners.getOrPut(event) { mutableListOf() }.add { c -> task.onPostExecution(c) }
        }
    }

    fun fire(event: SplitEvent) {
        listeners[event]?.forEach { it(this) }
    }

    fun subscribed(event: SplitEvent): Boolean = listeners.containsKey(event)
}

/**
 * Base test class that ensures MockK is cleaned up after each test.
 */
abstract class BaseMockkTest {
    @After
    fun tearDown() {
        unmockkAll()
    }
}

/**
 * Verifies that an event was sent to the /events endpoint with the expected data.
 *
 * @param recordedRequests List of all recorded requests from the mock server
 * @param eventName The expected event name
 * @param expectedValue The expected numeric value (null if not provided)
 * @param expectedProperties Map of expected property keys to values (null if not provided)
 * @param userKey The expected user key
 * @param trafficType The expected traffic type
 */
fun verifyEventSent(
    recordedRequests: List<RecordedRequest>,
    eventName: String,
    expectedValue: Double? = null,
    expectedProperties: Map<String, Any>? = null,
    userKey: String = "test-user",
    trafficType: String = "user"
) {
    // Find events requests
    val eventsRequests = recordedRequests.filter { it.path?.contains("/events/bulk") == true }
    assertTrue(
        "Expected at least one request to /events/bulk endpoint, but found ${eventsRequests.size}",
        eventsRequests.isNotEmpty()
    )

    // Parse the request body to verify event data
    var eventFound = false
    for (request in eventsRequests) {
        val body = request.body.readUtf8()
        if (body.isEmpty()) continue

        try {
            val jsonArray = JSONArray(body)
            for (i in 0 until jsonArray.length()) {
                val event = jsonArray.getJSONObject(i)

                if (event.optString("eventTypeId") == eventName &&
                    event.optString("key") == userKey &&
                    event.optString("trafficTypeName") == trafficType) {
                    
                    eventFound = true
                    
                    // Verify value if expected
                    if (expectedValue != null) {
                        val actualValue = event.optDouble("value", Double.NaN)
                        assertEquals(
                            "Event value mismatch for event '$eventName'",
                            expectedValue,
                            actualValue,
                            0.001
                        )
                    }
                    
                    // Verify properties if expected
                    if (expectedProperties != null) {
                        val properties = event.optJSONObject("properties")
                        assertTrue(
                            "Event '$eventName' should have properties",
                            properties != null
                        )
                        
                        for ((key, expectedVal) in expectedProperties) {
                            val actualVal = properties?.opt(key)
                            
                            // Compare values, handling type conversions
                            val valuesMatch = when {
                                expectedVal == actualVal -> true
                                expectedVal is String && actualVal is Number -> expectedVal == actualVal.toString()
                                expectedVal is Number && actualVal is String -> expectedVal.toString() == actualVal

                                else -> false
                            }
                            
                            assertTrue(
                                "Property '$key' mismatch for event '$eventName': expected '$expectedVal' (${expectedVal::class.simpleName}) but was '$actualVal' (${actualVal?.let { it::class.simpleName }})",
                                valuesMatch
                            )
                        }
                    }
                    
                    break
                }
            }
            
            if (eventFound) break
        } catch (e: Exception) {
            continue
        }
    }
    
    assertTrue(
        "Event '$eventName' with key '$userKey' and trafficType '$trafficType' was not found in events requests",
        eventFound
    )
}
