import java.util.Properties

plugins {
    id("com.android.library") version "8.1.4"
    id("org.jetbrains.kotlin.android") version "2.1.21"
    id("com.diffplug.spotless") version "7.0.4"
    jacoco
}

// Load version properties
val versionPropsFile = file("version.properties")
val versionProps = Properties()

if (versionPropsFile.exists()) {
    versionProps.load(versionPropsFile.inputStream())
}

val versionName = versionProps.getProperty("VERSION_NAME", "1.0-SNAPSHOT")
val versionCode = versionProps.getProperty("VERSION_CODE", "1").toInt()

group = "io.harness"
version = versionName

android {
    namespace = "io.harness.openfeature.split"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        // Set version info for BuildConfig
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "${versionCode}")
    }

    buildTypes {
        debug {
            isRenderscriptDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            proguardFiles (getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("io.split.client:android-client:5.3.0")
    implementation("dev.openfeature:android-sdk:0.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.5")
    
    // Robolectric for Android testing on JVM
    testImplementation("org.robolectric:robolectric:4.14.1")

    testImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}


spotless {
    kotlin {
        // version, style and all configurations here are optional
        ktfmt("0.51").googleStyle().configure {
            it.setMaxWidth(80)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
            it.setRemoveUnusedImports(false)
            it.setManageTrailingCommas(false)
        }
    }
}

// JaCoCo configuration
jacoco {
    toolVersion = "0.8.11" // Use the latest version of JaCoCo
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "Reporting"
    description = "Generate Jacoco coverage reports after running tests."

    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true) // Required for SonarQube
        html.required.set(true)
        csv.required.set(false)
    }

    val debugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug") {
        exclude(
            // Exclude common test-related files
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*"
        )
    }

    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.buildDir) {
        include("jacoco/testDebugUnitTest.exec")
    })
}

tasks.named("check") {
    dependsOn("jacocoTestReport")
}
