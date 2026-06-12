package com.nova.luna.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.luna.modelinstall.ModelInstallService
import com.nova.luna.modelinstall.ModelInstallSpecRegistry
import com.nova.luna.modelinstall.ModelInstallState
import com.nova.luna.modelinstall.ModelInstallReason
import com.nova.luna.safety.SafetyGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModelDiagnosticsProviderTest {

    @Test
    fun `diagnostics report missing models honestly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockService = mock(ModelInstallService::class.java)
        val registry = ModelInstallSpecRegistry()

        registry.allSpecs().forEach { spec ->
            doReturn(missingState(spec.modelId, spec.displayName, spec.role, spec.expectedFileName))
                .`when`(mockService)
                .getInstallState(spec.modelId)
        }

        val provider = ModelDiagnosticsProvider(context, mockService, SafetyGate())
        val result = provider.getDiagnostics()

        assertEquals("ERROR", result.overallStatus)
        assertEquals(registry.allSpecs().size, result.installedModels.size)
        assertTrue(result.installedModels.all { !it.ready })
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.all { it.contains("Model file missing") })
        assertTrue(result.safetyGateAvailable)
        assertTrue(result.brainRouterAvailable)
    }

    private fun missingState(
        modelId: String,
        displayName: String,
        role: String,
        expectedFileName: String
    ): ModelInstallState {
        return ModelInstallState(
            modelId = modelId,
            displayName = displayName,
            role = role,
            expectedFileName = expectedFileName,
            resolvedPath = null,
            exists = false,
            readable = false,
            sizeBytes = null,
            minimumBytes = 0,
            sha256Expected = null,
            sha256Actual = null,
            sha256Verified = false,
            extensionAllowed = false,
            ready = false,
            reason = ModelInstallReason.MODEL_FILE_MISSING
        )
    }
}
