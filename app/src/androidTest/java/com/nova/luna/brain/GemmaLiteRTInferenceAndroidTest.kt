package com.nova.luna.brain

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GemmaLiteRTInferenceAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun gemmaProducesReadableOutput() {
        // Use internal storage where we copied the model
        val modelFile = File(context.filesDir, "gemma.litertlm")
        
        assertTrue("Model file does not exist at ${modelFile.absolutePath}", modelFile.exists())

        val config = GemmaPhoneConfig(
            gemmaEnabled = true,
            gemmaRealInferenceEnabled = true,
            gemmaModelAssetPath = modelFile.absolutePath,
            gemmaMaxTokens = 64,
            gemmaTemperature = 0.2,
            gemmaTopK = 40,
            gemmaContextWindow = 8192,
            gemmaRoleEnabled = true
        )

        val backend = LiteRTGemmaRuntimeBackend(context)
        val prompt = "Reply with exactly: GEMMA_BRAIN_OK"
        
        Log.i("GemmaTest", "Starting inference with model: ${modelFile.absolutePath}")
        val startTime = System.currentTimeMillis()
        val response = backend.generate(prompt, config)
        val duration = System.currentTimeMillis() - startTime
        Log.i("GemmaTest", "Inference took ${duration}ms")
        Log.i("GemmaTest", "Response: $response")

        assertNotNull("Response should not be null", response)
        assertFalse("Response should not be an error: $response", response.startsWith("Error:"))
        
        // Gemma might wrap the output or add some text, so we check if it contains the marker.
        assertTrue("Response should contain marker 'GEMMA_BRAIN_OK'. Got: '$response'", 
            response.contains("GEMMA_BRAIN_OK", ignoreCase = true))
    }
}
