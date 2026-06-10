package com.nova.luna.modelinstall

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest

class HttpModelDownloaderTest {
    private data class FakeResponse(
        val responseCode: Int,
        val body: ByteArray = ByteArray(0),
        val message: String = when (responseCode) {
            in 200..299 -> "OK"
            500 -> "Internal Server Error"
            else -> "HTTP $responseCode"
        }
    )

    private lateinit var baseDir: File
    private lateinit var storage: PrivateAppModelStorage
    private lateinit var stateStore: DownloadStateStore
    private lateinit var registry: LocalModelRegistry
    private var responses: Map<String, FakeResponse> = emptyMap()
    private lateinit var downloader: HttpModelDownloader

    @Before
    fun setUp() {
        baseDir = Files.createTempDirectory("nova_luna_model_downloader_test").toFile()
        storage = PrivateAppModelStorage.from(baseDir)
        stateStore = DownloadStateStore(storage)
        registry = LocalModelRegistry(storage)
        downloader = HttpModelDownloader(
            storage = storage,
            stateStore = stateStore,
            registry = registry,
            verifier = Sha256ModelVerifier(),
            connectionFactory = { url -> FakeHttpURLConnection(url, responses[url.path] ?: error("Unexpected path: ${url.path}")) }
        )
    }

    @After
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    @Test
    fun writesFileToPrivateModelStoragePathAndMarksSuccess() {
        val payload = "Nova Luna phase 2 payload".toByteArray()
        responses = mapOf("/success" to FakeResponse(200, payload))
        val source = sourceFor(
            path = "/success",
            payload = payload,
            sourceId = "core-success"
        )

        val events = mutableListOf<ModelDownloadStatus>()
        val result = downloader.download(source) { events += it.status }

        assertEquals(ModelDownloadStatus.SUCCESS, result.status)
        assertTrue(events.contains(ModelDownloadStatus.DOWNLOADING))
        assertTrue(events.contains(ModelDownloadStatus.VERIFYING))
        assertEquals(ModelDownloadStatus.SUCCESS, events.last())

        val finalFile = storage.packFile(ModelPackId.CORE, source.fileName)
        val stagedFile = storage.stagedFile(ModelPackId.CORE, "${source.fileName}.part")

        assertTrue(finalFile.exists())
        assertFalse(stagedFile.exists())
        assertTrue(finalFile.canonicalPath.startsWith(storage.rootDir.canonicalPath))

        val persisted = stateStore.find(ModelPackId.CORE, source.sourceId)
        assertNotNull(persisted)
        assertEquals(ModelDownloadStatus.SUCCESS, persisted!!.status)
        assertTrue(registry.hasReadyPack(ModelPackId.CORE))
    }

    @Test
    fun stateChangesToFailedWhenDownloadFails() {
        val payload = "ignored".toByteArray()
        responses = mapOf("/download-fails" to FakeResponse(500, payload))
        val source = sourceFor(
            path = "/download-fails",
            payload = payload,
            sourceId = "core-download-fails"
        )

        val result = downloader.download(source)

        assertEquals(ModelDownloadStatus.FAILED, result.status)
        assertEquals(ModelDownloadStatus.FAILED, stateStore.find(ModelPackId.CORE, source.sourceId)!!.status)
        assertTrue(registry.readyPacks().isEmpty())
        assertFalse(storage.packFile(ModelPackId.CORE, source.fileName).exists())
        assertFalse(storage.stagedFile(ModelPackId.CORE, "${source.fileName}.part").exists())
    }

    @Test
    fun stateChangesToFailedWhenVerificationFailsAndPartialFileIsNotMarkedReady() {
        val goodPayload = "verified payload".toByteArray()
        val partialPayload = goodPayload.copyOfRange(0, goodPayload.size / 2)
        responses = mapOf("/partial" to FakeResponse(200, partialPayload))
        val source = sourceFor(
            path = "/partial",
            payload = partialPayload,
            sourceId = "core-partial",
            expectedSha256 = sha256Hex(goodPayload),
            expectedByteCount = goodPayload.size.toLong()
        )

        val result = downloader.download(source)

        assertEquals(ModelDownloadStatus.FAILED, result.status)
        assertEquals(ModelDownloadStatus.FAILED, stateStore.find(ModelPackId.CORE, source.sourceId)!!.status)
        assertTrue(registry.readyPacks().isEmpty())
        assertFalse(storage.packFile(ModelPackId.CORE, source.fileName).exists())
        assertFalse(storage.stagedFile(ModelPackId.CORE, "${source.fileName}.part").exists())
    }

    @Test
    fun noModelPackIsReadyUntilVerifierPasses() {
        val goodPayload = "ready only after hash".toByteArray()
        responses = mapOf(
            "/bad" to FakeResponse(200, goodPayload.copyOfRange(0, goodPayload.size / 2)),
            "/good" to FakeResponse(200, goodPayload)
        )
        val badSource = sourceFor(
            path = "/bad",
            payload = goodPayload.copyOfRange(0, goodPayload.size / 2),
            sourceId = "core-bad",
            expectedSha256 = sha256Hex(goodPayload),
            expectedByteCount = goodPayload.size.toLong()
        )
        val goodSource = sourceFor(
            path = "/good",
            payload = goodPayload,
            sourceId = "core-good",
            expectedSha256 = sha256Hex(goodPayload),
            expectedByteCount = goodPayload.size.toLong()
        )

        assertTrue(registry.readyPacks().isEmpty())

        val failed = downloader.download(badSource)
        assertEquals(ModelDownloadStatus.FAILED, failed.status)
        assertTrue(registry.readyPacks().isEmpty())

        val success = downloader.download(goodSource)
        assertEquals(ModelDownloadStatus.SUCCESS, success.status)
        assertEquals(listOf(ModelPackId.CORE), registry.readyPacks())
    }

    @Test
    fun stateChangesToFailedWhenDownloadUrlIsMissing() {
        val payload = "missing url payload".toByteArray()
        val source = sourceFor(
            path = "/missing-url",
            payload = payload,
            sourceId = "core-missing-url",
            downloadUrl = null
        )

        val result = downloader.download(source)

        assertEquals(ModelDownloadStatus.FAILED, result.status)
        assertEquals(ModelDownloadStatus.FAILED, stateStore.find(ModelPackId.CORE, source.sourceId)!!.status)
        assertTrue(registry.readyPacks().isEmpty())
        assertFalse(storage.packFile(ModelPackId.CORE, source.fileName).exists())
    }

    @Test
    fun stateChangesToFailedWhenSha256IsMissing() {
        val payload = "missing sha payload".toByteArray()
        responses = mapOf("/missing-sha" to FakeResponse(200, payload))
        val source = sourceFor(
            path = "/missing-sha",
            payload = payload,
            sourceId = "core-missing-sha",
            expectedSha256 = null
        )

        val result = downloader.download(source)

        assertEquals(ModelDownloadStatus.FAILED, result.status)
        assertEquals(ModelDownloadStatus.FAILED, stateStore.find(ModelPackId.CORE, source.sourceId)!!.status)
        assertTrue(registry.readyPacks().isEmpty())
        assertFalse(storage.packFile(ModelPackId.CORE, source.fileName).exists())
    }

    @Test
    fun stateChangesToFailedWhenExpectedSizeIsMissing() {
        val payload = "missing size payload".toByteArray()
        responses = mapOf("/missing-size" to FakeResponse(200, payload))
        val source = sourceFor(
            path = "/missing-size",
            payload = payload,
            sourceId = "core-missing-size",
            expectedByteCount = null
        )

        val result = downloader.download(source)

        assertEquals(ModelDownloadStatus.FAILED, result.status)
        assertEquals(ModelDownloadStatus.FAILED, stateStore.find(ModelPackId.CORE, source.sourceId)!!.status)
        assertTrue(registry.readyPacks().isEmpty())
        assertFalse(storage.packFile(ModelPackId.CORE, source.fileName).exists())
    }

    @Test
    fun stateChangesToFailedWhenDownloadedSizeDoesNotMatchExpectedSize() {
        val goodPayload = "expected size payload".toByteArray()
        val partialPayload = goodPayload.copyOfRange(0, goodPayload.size / 2)
        responses = mapOf("/size-mismatch" to FakeResponse(200, partialPayload))
        val source = sourceFor(
            path = "/size-mismatch",
            payload = goodPayload,
            sourceId = "core-size-mismatch",
            expectedSha256 = sha256Hex(partialPayload),
            expectedByteCount = goodPayload.size.toLong()
        )

        val result = downloader.download(source)

        assertEquals(ModelDownloadStatus.FAILED, result.status)
        assertEquals(ModelDownloadStatus.FAILED, stateStore.find(ModelPackId.CORE, source.sourceId)!!.status)
        assertTrue(registry.readyPacks().isEmpty())
        assertFalse(storage.packFile(ModelPackId.CORE, source.fileName).exists())
        assertFalse(storage.stagedFile(ModelPackId.CORE, "${source.fileName}.part").exists())
    }

    private fun sourceFor(
        path: String,
        payload: ByteArray,
        sourceId: String,
        downloadUrl: String? = "http://local$path",
        expectedSha256: String? = sha256Hex(payload),
        expectedByteCount: Long? = payload.size.toLong()
    ): ModelDownloadSource {
        return ModelDownloadSource(
            packId = ModelPackId.CORE,
            packDisplayName = "Core",
            sourceId = sourceId,
            fileName = "gemma-3n-q4.gguf",
            relativePath = "core",
            downloadUrl = downloadUrl,
            expectedSha256 = expectedSha256,
            expectedByteCount = expectedByteCount,
            notes = listOf("Test source")
        ).normalized()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val response: FakeResponse
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() {
            connected = true
        }

        override fun getInputStream(): InputStream {
            if (response.responseCode !in 200..299) {
                throw IOException("Cannot read body for HTTP ${response.responseCode}")
            }
            return ByteArrayInputStream(response.body)
        }

        override fun getResponseCode(): Int {
            return response.responseCode
        }

        override fun getResponseMessage(): String {
            return response.message
        }

        override fun getContentLengthLong(): Long {
            return response.body.size.toLong()
        }
    }
}
