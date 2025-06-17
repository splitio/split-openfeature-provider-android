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

## Submitting issues

The Split team monitors all issues submitted to this [issue tracker](https://github.com/splitio/split-openfeature-provider-android/issues). We encourage you to use this issue tracker to submit any bug reports, feedback, and feature enhancements. We'll do our best to respond in a timely manner.

## Contributing
Please see [Contributors Guide](CONTRIBUTORS-GUIDE.md) to find all you need to submit a Pull Request (PR).

## License
Licensed under the Apache License, Version 2.0. See: [Apache License](http://www.apache.org/licenses/).

## About Harness FME (Formerly Split.io)

Harness Feature Management and Experimentation (formerly Split) is a leading Feature Delivery Platform for engineering teams that want to confidently deploy features as fast as they can develop them. Split’s fine-grained management, real-time monitoring, and data-driven experimentation ensure that new features will improve the customer experience without breaking or degrading performance. Companies like Twilio, Salesforce, GoDaddy and WePay have trusted Split to power their feature delivery.

To learn more about Harness Feature Management and Experimentation (formerly Split), visit the [Harness website](https://www.harness.io/products/feature-management-experimentation) or contact [Harness Sales](https://harness.io/contact/sales).

Harness  has built and maintains SDKs for:

* Java [Github](https://github.com/splitio/java-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/java-sdk)
* Javascript [Github](https://github.com/splitio/javascript-client) [Docs]()
* Node [Github](https://github.com/splitio/javascript-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/nodejs-sdk)
* .NET [Github](https://github.com/splitio/dotnet-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/net-sdk)
* Ruby [Github](https://github.com/splitio/ruby-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/ruby-sdk)
* PHP [Github](https://github.com/splitio/php-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/php-sdk)
* Python [Github](https://github.com/splitio/python-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/python-sdk)
* GO [Github](https://github.com/splitio/go-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/server-side-sdks/go-sdk)
* Android [Github](https://github.com/splitio/android-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/client-side-sdks/android-sdk)
* iOS [Github](https://github.com/splitio/ios-client) [Docs](https://developer.harness.io/docs/feature-management-experimentation/sdks-and-infrastructure/client-side-sdks/ios-sdk)

For a comprehensive list of open source projects visit our [Github page](https://github.com/splitio?utf8=%E2%9C%93&query=%20only%3Apublic%20).

**Learn more about Harness:**

Visit [harness.io](https://www.harness.io) for an overview of Harness, or visit our documentation at [developer.harness.io/docs](https://developer.harness.io/docs) for more detailed information.
