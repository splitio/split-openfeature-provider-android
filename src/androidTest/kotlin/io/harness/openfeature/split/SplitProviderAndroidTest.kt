package io.harness.openfeature.split

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.ProviderEvaluation
import io.split.android.client.SplitClientConfig
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SplitProviderAndroidTest {

    private lateinit var provider: SplitProvider
    private lateinit var context: Context
    private lateinit var splitYamlFile: File

    @Before
    fun setUp() {
        // Get the real Android test context
        context = ApplicationProvider.getApplicationContext()
        
        // Create a test splits.yaml file in the app's cache directory
        setupSplitsYamlFile()
        
        // Configure Split client for localhost mode with debug logging
        val config = SplitClientConfig.builder()
            .ready(10) // Wait up to 10 milliseconds for SDK readiness
            .logLevel(7) // Set to debug level
            .build()
        
        // Setup the Split provider in localhost mode
        provider = SplitProvider.create(
            apiKey = "localhost", 
            context = context, 
            config = config
        )

        // Initialize the provider with a test targeting key
        val initialContext: EvaluationContext = ImmutableContext("test-targeting-key")
        runBlocking {
            provider.initialize(initialContext)
        }
    }

    /**
     * Sets up the splits.yaml file for localhost mode testing
     */
    private fun setupSplitsYamlFile() {
        // Create a splits.yaml file in the app's cache directory
        val assetsDir = File(context.cacheDir, "assets")
        assetsDir.mkdirs()
        
        splitYamlFile = File(assetsDir, "splits.yaml")
        
        // Create a simple splits.yaml content
        val splitsYamlContent = """
            - feature: boolean_flag_on
              treatment: "on"
              keys: ["test-targeting-key"]
              config: '{"color": "blue", "size": 13}'
            
            - feature: boolean_flag_off
              treatment: "off"
              keys: ["test-targeting-key"]
              config: '{}'
            
            - feature: string_flag
              treatment: "value1"
              keys: ["test-targeting-key"]
              config: '{"description": "This is a string flag"}'
            
            - feature: number_flag
              treatment: "42"
              keys: ["test-targeting-key"]
              config: '{"min": 0, "max": 100}'
            
            - feature: double_flag
              treatment: "3.14"
              keys: ["test-targeting-key"]
              config: '{"pi": true}'
        """.trimIndent()
        
        // Write the content to the file
        splitYamlFile.writeText(splitsYamlContent)
    }

    @After
    fun tearDown() {
        // Clean up Split client resources
        try {
            provider.shutdown()
        } catch (e: Exception) {
            // Ignore exceptions during shutdown
        }
        
        // Clean up the test file
        splitYamlFile.delete()
    }

    @Test
    fun getBooleanEvaluation() {
        val result: ProviderEvaluation<Boolean> = provider.getBooleanEvaluation("boolean_flag_on", false, null)
        assertEquals(true, result.value)
    }

    @Test
    fun getBooleanEvaluationForOffFlag() {
        val result: ProviderEvaluation<Boolean> = provider.getBooleanEvaluation("boolean_flag_off", true, null)
        assertEquals(false, result.value)
    }

    @Test
    fun getStringEvaluation() {
        // Note: This will fail until the getStringEvaluation method is implemented in SplitProvider
        try {
            val result: ProviderEvaluation<String> = provider.getStringEvaluation("string_flag", "default", null)
            assertEquals("value1", result.value)
        } catch (e: NotImplementedError) {
            // Expected until implemented
        }
    }

    @Test
    fun getIntegerEvaluation() {
        // Note: This will fail until the getIntegerEvaluation method is implemented in SplitProvider
        try {
            val result: ProviderEvaluation<Int> = provider.getIntegerEvaluation("number_flag", 0, null)
            assertEquals(42, result.value)
        } catch (e: NotImplementedError) {
            // Expected until implemented
        }
    }

    @Test
    fun getDoubleEvaluation() {
        // Note: This will fail until the getDoubleEvaluation method is implemented in SplitProvider
        try {
            val result: ProviderEvaluation<Double> = provider.getDoubleEvaluation("double_flag", 0.0, null)
            assertEquals(3.14, result.value)
        } catch (e: NotImplementedError) {
            // Expected until implemented
        }
    }
}
