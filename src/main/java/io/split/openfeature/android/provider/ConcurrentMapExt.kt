package io.split.openfeature.android.provider

import java.util.concurrent.ConcurrentHashMap

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
