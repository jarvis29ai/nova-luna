package com.nova.luna.modelinstall

import com.nova.luna.brain.BrainRequest
import com.nova.luna.model.BrainModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInstallBrainRouterBridgeTest {
    @Test
    fun `selects core brain when core pack is runtime ready`() {
        val payload = "core brain payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val bridge = ModelInstallBrainRouterBridge(env.modelInstallService)
            val decision = bridge.selectLocalRoute(
                BrainRequest("please explain how offline model verification works")
            )

            assertEquals(BrainModelRole.CORE_BRAIN, decision?.selectedRole)
            assertTrue(decision?.reason?.contains("Core Brain", ignoreCase = true) == true)
        }
    }

    @Test
    fun `selects multilingual backup for hindi request when full pack is runtime ready`() {
        val payload = "multilingual payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.FULL,
            fileName = "qwen-3-small-q4.gguf",
            relativePath = "full/multilingual",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("full/multilingual/qwen-3-small-q4.gguf" to payload)
            )

            val bridge = ModelInstallBrainRouterBridge(env.modelInstallService)
            val decision = bridge.selectLocalRoute(BrainRequest("कृपया मुझे समझाओ"))

            assertEquals(BrainModelRole.MULTILINGUAL_BACKUP, decision?.selectedRole)
            assertTrue(decision?.reason?.contains("multilingual", ignoreCase = true) == true)
        }
    }

    @Test
    fun `selects lite fallback when only lite pack is runtime ready`() {
        val payload = "lite payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.LITE,
            fileName = "gemma-3-270m-q4.gguf",
            relativePath = "lite",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("lite/gemma-3-270m-q4.gguf" to payload)
            )

            val bridge = ModelInstallBrainRouterBridge(env.modelInstallService)
            val decision = bridge.selectLocalRoute(BrainRequest("simple fallback help"))

            assertEquals(BrainModelRole.LITE_FALLBACK, decision?.selectedRole)
            assertTrue(decision?.reason?.contains("fallback", ignoreCase = true) == true)
        }
    }

    @Test
    fun `returns null when no local pack is runtime ready`() {
        val payload = "missing pack payload".toByteArray()
        val pack = singleFilePack(
            packId = ModelPackId.CORE,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            payload = payload
        )

        withLocalRuntimeEnvironment(catalog = listOf(pack)) { env ->
            val bridge = ModelInstallBrainRouterBridge(env.modelInstallService)
            val decision = bridge.selectLocalRoute(BrainRequest("please explain how offline verification works"))

            assertNull(decision)
        }
    }
}
