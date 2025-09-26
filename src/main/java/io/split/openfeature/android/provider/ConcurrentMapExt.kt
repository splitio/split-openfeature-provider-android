package io.split.openfeature.android.provider

import java.util.concurrent.ConcurrentHashMap

/**
 * Atomically returns the existing value associated with [key], or stores and returns the value
 * produced by [defaultValue] if absent. Uses a synchronized block for broad Android compatibility.
 */
internal inline fun <K, V : Any> ConcurrentHashMap<K, V>.getOrPutConcurrent(
    key: K,
    defaultValue: () -> V
): V {
    synchronized(this) {
        val existing = this[key]
        if (existing != null) {
            return existing
        }
        val created = defaultValue()
        this[key] = created
        return created
    }
}
