plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.33.0"
    id("signing")
}

android {
    namespace = "io.split.openfeature.android.provider"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.split.sdk)
    implementation(libs.openfeature.sdk)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.junit)
}

val providerVersion = "1.0.0"

val splitPOM = Action<MavenPom> {
    name.set("Split OpenFeature Provider for Android")
    packaging = "aar"
    description.set("Official Split OpenFeature Provider for Android")
    url.set("https://github.com/splitio/split-openfeature-provider-android")

    licenses {
        license {
            name.set("Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }

    developers {
        developer {
            id.set("splitio")
            name.set("Split Software")
            email.set("sdks@split.io")
        }
    }

    scm {
        connection.set("scm:git:git://github.com/splitio/split-openfeature-provider-android.git")
        developerConnection.set("scm:git:ssh://github.com:splitio/split-openfeature-provider-android.git")
        url.set("https://github.com/splitio/split-openfeature-provider-android")
    }
}

mavenPublishing {
    coordinates("io.split.openfeature", "split-openfeature-provider-android", providerVersion)
    pom(splitPOM)

    publishToMavenCentral(false)
    signAllPublications()
}
