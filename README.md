# Split OpenFeature Provider for Android

This library provides an [OpenFeature](https://openfeature.dev/) provider implementation for the [Split](https://www.split.io/) feature flag system on Android.

## Installation

Add the dependency to your app's build.gradle file:

```kotlin
dependencies {
    implementation("io.harness:split-openfeature-provider-android:1.0-SNAPSHOT")
}
```

## Usage

### Initialize the Split Provider

```kotlin
// Initialize Split client
val config = SplitClientConfig.builder()
    .build()
val splitFactory = SplitFactory.get("YOUR_API_KEY", config)
val splitClient = splitFactory.client()

// Create the Split provider
val splitProvider = SplitProvider.builder()
    .withSplitClient(splitClient)
    .build()

// Register the provider with OpenFeature
OpenFeatureAPI.getInstance().setProvider(splitProvider)
```

### Evaluate Feature Flags

```kotlin
// Get the client
val client = OpenFeatureAPI.getInstance().getClient()

// Evaluate boolean flag
val booleanValue = client.getBooleanValue("my-boolean-flag", false)

// Evaluate string flag
val stringValue = client.getStringValue("my-string-flag", "default")

// Evaluate integer flag
val intValue = client.getIntegerValue("my-int-flag", 0)

// Evaluate double flag
val doubleValue = client.getDoubleValue("my-double-flag", 0.0)
```

## Building from Source

This project uses Gradle for building. To build the library:

```bash
./gradlew build
```

To publish the library to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE.txt](LICENSE.txt) file for details.
