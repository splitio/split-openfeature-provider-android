import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.mavenpublish)
    alias(libs.plugins.signing)
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.12"
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
    kotlinOptions { jvmTarget = "11" }
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
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)
    androidTestImplementation(libs.androidx.junit)
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
        isEnabled = true
    }
}

// Unit-test JaCoCo XML only. hcli accepts jacoco.xml under build/reports/jacoco/test.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates a JaCoCo XML report for debug unit tests."

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacoco.xml"))
    }

    val coverageExclusions = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )
    val classDirs = listOf(
        "tmp/kotlin-classes/debug",
        "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        "intermediates/javac/debug/compileDebugJavaWithJavac/classes"
    )

    classDirectories.setFrom(
        files(classDirs.map { dir ->
            fileTree(layout.buildDirectory.dir(dir)) { exclude(coverageExclusions) }
        })
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/**/*.exec"
            )
        }
    )
}

afterEvaluate {
    tasks.named("testDebugUnitTest") {
        finalizedBy("jacocoTestReport")
    }
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
    coordinates("io.split.openfeature", "split-openfeature-android", providerVersion)
    pom(splitPOM)

    publishToMavenCentral(false)
    signAllPublications()
}
