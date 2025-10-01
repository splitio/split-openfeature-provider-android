# Split OpenFeature Provider for Android

## Overview
This Provider is designed to enable the use of OpenFeature in Android, with Split as the backing feature flag & experimentation platform.

## Compatibility
The Split OpenFeature Provider supports:
- Android API Level 21 (Android 5.0 Lollipop) and higher

## Getting started
Below is a simple example that describes the instantiation of the Split Provider. Please see the [OpenFeature Documentation](https://docs.openfeature.dev/docs/reference/concepts/evaluation-api) for details on how to use the OpenFeature SDK.

### Installation

Add the Split OpenFeature Provider dependency to your app's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("io.split.openfeature:split-openfeature-android:1.0.0")
}
```

Or for Groovy DSL (`build.gradle`):

```groovy
dependencies {
    implementation 'io.split.openfeature:split-openfeature-android:1.0.0'
}
```

### Usage

The Split OpenFeature Provider requires an Android `Context` and your Split SDK key. You must also provide an evaluation context with a targeting key when initializing the provider.

```kotlin
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.ImmutableContext
import io.split.openfeature.android.provider.SplitProvider

// Create provider configuration
val config = SplitProvider.Config(
    applicationContext = applicationContext,
    sdkKey = "YOUR_SDK_KEY"
)

// Create the Split provider
val provider = SplitProvider(config = config)

// Set the provider with an initial context containing a targeting key
val initialContext = ImmutableContext(targetingKey = "user-123")
OpenFeatureAPI.setProvider(provider, initialContext = initialContext)

// Get a client and evaluate flags
val client = OpenFeatureAPI.getClient()
val showNewFeature = client.getBooleanValue("new-feature", false)
```

### Evaluation Context

The Split OpenFeature Provider requires a targeting key to be set in the evaluation context. This key identifies the user or entity for which you are evaluating feature flags.

#### Setting a targeting key during initialization:

```kotlin
val initialContext = ImmutableContext(targetingKey = "user-123")
OpenFeatureAPI.setProvider(provider, initialContext = initialContext)
```

#### Changing the targeting key at runtime:

```kotlin
val newContext = ImmutableContext(targetingKey = "user-456")
OpenFeatureAPI.setEvaluationContext(newContext)
```

#### Using attributes for targeting:

```kotlin
val context = ImmutableContext(
    targetingKey = "user-123",
    attributes = mapOf(
        "email" to Value.String("user@example.com"),
        "age" to Value.Integer(30),
        "plan" to Value.String("premium")
    )
)

val client = OpenFeatureAPI.getClient()
val result = client.getBooleanDetails("premium-feature", false, context)
```

### Observing Provider Events

The Split OpenFeature Provider emits events when the provider state changes. You can observe these events to react to provider readiness, configuration changes, or errors.

```kotlin
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import kotlinx.coroutines.launch

// Observe provider events
lifecycleScope.launch {
    provider.observe().collect { event ->
        when (event) {
            is OpenFeatureProviderEvents.ProviderReady -> {
                // Provider is ready to evaluate flags
                Log.d("Split", "Provider is ready")
            }
            is OpenFeatureProviderEvents.ProviderConfigurationChanged -> {
                // Flag configuration has been updated
                Log.d("Split", "Configuration changed")
            }
            is OpenFeatureProviderEvents.ProviderStale -> {
                // Provider is serving cached data
                Log.d("Split", "Provider is stale")
            }
            is OpenFeatureProviderEvents.ProviderError -> {
                // An error occurred
                Log.e("Split", "Provider error: ${event.error}")
            }
        }
    }
}
```

### Tracking Events

The Split OpenFeature Provider supports tracking events. To track events, you must set a `trafficType` in the evaluation context.

```kotlin
// Set context with trafficType
val context = ImmutableContext(targetingKey = "user-123")
    .withTrafficType("user")

OpenFeatureAPI.setEvaluationContext(context)

// Track an event
val client = OpenFeatureAPI.getClient()
client.track("button_clicked")

// Track with a value
client.track(
    "purchase_completed",
    TrackingEventDetails(value = 99.99)
)

// Track with properties
client.track(
    "page_viewed",
    TrackingEventDetails(
        structure = ImmutableStructure(
            mapOf(
                "page" to Value.String("home"),
                "referrer" to Value.String("google")
            )
        )
    )
)
```

## Contributing
Please see [Contributors Guide](CONTRIBUTORS-GUIDE.md) to find all you need to submit a Pull Request (PR).

## License
Licensed under the Apache License, Version 2.0. See: [Apache License](http://www.apache.org/licenses/LICENSE-2.0).
