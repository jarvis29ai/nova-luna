package com.nova.luna.modelinstall

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelMaintenancePhase8Test {
    @Test
    fun updateModelReplacesReadyPackAfterVerifiedDownload() {
        val payload = fileBytes("core model payload")
        val oldPack = corePack(
            description = "Core maintenance phase 8 previous release",
            payload = payload
        )
        val newPack = corePack(
            description = "Core maintenance phase 8 current release",
            payload = payload
        )

        withMaintenanceEnvironment(
            catalog = listOf(newPack),
            responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, payload))
        ) { env ->
            seedReadyPack(
                env = env,
                pack = oldPack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val before = env.coordinator.getInstallStatus(ModelPackId.CORE)
            assertTrue(before.ready)
            assertNotEquals(oldPack.versionTag(), newPack.versionTag())

            val result = env.manager.updateModel(ModelPackId.CORE)

            assertTrue(result.ready)
            assertEquals(newPack.versionTag(), result.installedVersion)
            assertEquals(newPack.versionTag(), env.stateStore.find(ModelPackId.CORE)!!.version)
            assertEquals(newPack.versionTag(), env.registry.find(ModelPackId.CORE)!!.version)
            assertArrayEquals(payload, env.storage.packFile(ModelPackId.CORE, "gemma-3n-q4.gguf").readBytes())
            assertEquals(ModelUserFacingState.READY, env.manager.getUserSafeState(ModelPackId.CORE).state)
            assertEquals("Core is ready.", env.manager.getUserSafeState(ModelPackId.CORE).message)
        }
    }

    @Test
    fun failedUpdateLeavesCurrentPackReadyAndUnchanged() {
        val payload = fileBytes("core model payload")
        val oldPack = corePack(
            description = "Core maintenance phase 8 stable release",
            payload = payload
        )
        val newPack = corePack(
            description = "Core maintenance phase 8 staged release",
            payload = payload
        )

        withMaintenanceEnvironment(
            catalog = listOf(newPack),
            responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(500, payload))
        ) { env ->
            seedReadyPack(
                env = env,
                pack = oldPack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            val result = env.manager.updateModel(ModelPackId.CORE)

            assertTrue(result.ready)
            assertEquals(oldPack.versionTag(), result.installedVersion)
            assertEquals(oldPack.versionTag(), env.stateStore.find(ModelPackId.CORE)!!.version)
            assertEquals(oldPack.versionTag(), env.registry.find(ModelPackId.CORE)!!.version)
            assertArrayEquals(payload, env.storage.packFile(ModelPackId.CORE, "gemma-3n-q4.gguf").readBytes())
            assertEquals(ModelUserFacingState.READY, env.manager.getUserSafeState(ModelPackId.CORE).state)
        }
    }

    @Test
    fun repairModelRestoresCorruptPackAfterVerifiedRedownload() {
        val payload = fileBytes("core repair payload")
        val pack = corePack(
            description = "Core maintenance phase 8 repair target",
            payload = payload
        )

        withMaintenanceEnvironment(
            catalog = listOf(pack),
            responses = mapOf("/core/gemma-3n-q4.gguf" to FakeResponse(200, payload))
        ) { env ->
            seedReadyPack(
                env = env,
                pack = pack,
                payloads = mapOf("core/gemma-3n-q4.gguf" to payload)
            )

            env.storage.packFile(ModelPackId.CORE, "gemma-3n-q4.gguf").writeBytes(fileBytes("corrupt payload"))
            val corruptStatus = env.coordinator.getInstallStatus(ModelPackId.CORE)
            assertEquals(ModelRuntimeStatus.CORRUPT, corruptStatus.runtimeStatus)

            val result = env.manager.repairModel(ModelPackId.CORE)

            assertTrue(result.ready)
            assertEquals(ModelRuntimeStatus.READY, result.runtimeStatus)
            assertEquals(pack.versionTag(), env.stateStore.find(ModelPackId.CORE)!!.version)
            assertArrayEquals(payload, env.storage.packFile(ModelPackId.CORE, "gemma-3n-q4.gguf").readBytes())
            assertEquals(ModelUserFacingState.READY, env.manager.getUserSafeState(ModelPackId.CORE).state)
        }
    }

    @Test
    fun cleanupInactiveVersionsRemovesMaintenanceArtifacts() {
        val payload = fileBytes("cleanup payload")
        val pack = corePack(
            description = "Core maintenance phase 8 cleanup target",
            payload = payload
        )

        withMaintenanceEnvironment(catalog = listOf(pack)) { env ->
            val staleStaging = env.cleanupPolicy.stagingRoot(ModelPackId.CORE, "old-version")
            val staleBackup = env.cleanupPolicy.backupRoot(ModelPackId.CORE, "old-version")
            val staleStagingFile = File(staleStaging, "orphan.bin")
            val staleBackupFile = File(staleBackup, "backup.bin")

            staleStagingFile.parentFile?.mkdirs()
            staleBackupFile.parentFile?.mkdirs()
            staleStagingFile.writeBytes(fileBytes("stale staging"))
            staleBackupFile.writeBytes(fileBytes("stale backup"))

            val cleanup = env.cleanupPolicy.cleanupInactiveVersions(
                packId = ModelPackId.CORE,
                activeVersion = pack.versionTag()
            )

            assertTrue(cleanup.success)
            assertTrue(cleanup.deletedPaths.isNotEmpty())
            assertFalse(staleStaging.exists())
            assertFalse(staleBackup.exists())
            assertTrue(env.storage.modelsDir(ModelPackId.CORE).exists())
        }
    }

    @Test
    fun userSafeStateHidesTechnicalDetails() {
        val payload = fileBytes("safe status payload")
        val pack = corePack(
            description = "Core maintenance phase 8 user-safe target",
            payload = payload
        )

        withMaintenanceEnvironment(catalog = listOf(pack)) { env ->
            env.stateStore.markUnavailable(
                pack = pack,
                message = "C:\\Users\\cricv\\Desktop\\nova-luna\\app\\build\\cache\\model.bin failed",
                expectedFileCount = 1,
                verifiedFileCount = 0,
                missingFileCount = 0,
                corruptFileCount = 0
            )

            val status = env.manager.getUserSafeState(ModelPackId.CORE)

            assertEquals(ModelUserFacingState.UNAVAILABLE, status.state)
            assertEquals("Core is unavailable.", status.message)
            assertFalse(status.message.contains("\\"))
            assertFalse(status.message.contains("model.bin", ignoreCase = true))
            assertFalse(status.message.contains("checksum", ignoreCase = true))
        }
    }

    private fun corePack(
        description: String,
        payload: ByteArray
    ): ModelPackSpec {
        return ModelPackSpec(
            id = ModelPackId.CORE,
            displayName = "Core",
            description = description,
            requirement = ModelPackRequirement(
                minRamMb = 1,
                minFreeStorageMb = 1
            ),
            files = listOf(
                ModelFileSpec(
                    fileName = "gemma-3n-q4.gguf",
                    relativePath = "core",
                    sha256 = sha256Hex(payload),
                    byteCount = payload.size.toLong()
                )
            )
        ).normalized()
    }
}
