package io.split.openfeature.android.provider

import io.mockk.mockk
import io.mockk.unmockkAll
import io.split.android.client.SplitClient
import io.split.android.client.events.SplitEvent
import io.split.android.client.events.SplitEventTask
import org.junit.After

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
