package com.nova.luna.modelinstall

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class HttpModelDownloader(
    private val storage: PrivateAppModelStorage,
    private val stateStore: DownloadStateStore = DownloadStateStore(storage),
    private val registry: LocalModelRegistry = LocalModelRegistry(storage),
    private val verifier: Sha256ModelVerifier = Sha256ModelVerifier(),
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        (url.openConnection() as HttpURLConnection)
    }
) {
    fun download(
        source: ModelDownloadSource,
        cancelRequested: () -> Boolean = { false },
        onStateChanged: (ModelDownloadState) -> Unit = {}
    ): ModelDownloadState {
        val normalized = source.normalized()
        val urlString = normalized.downloadUrl
            ?: return fail(
                source = normalized,
                message = "Download URL is not configured."
            ).also(onStateChanged)

        val downloadUrl = runCatching { URL(urlString) }
            .getOrElse { throwable ->
                return fail(normalized, message = "Invalid download URL: ${throwable.message.orEmpty()}")
                    .also(onStateChanged)
            }

        val stagedFile = storage.stagedFile(normalized.packId, "${normalized.fileName}.part")
        val finalFile = storage.packFile(normalized.packId, normalized.fileName)
        stagedFile.parentFile?.mkdirs()
        finalFile.parentFile?.mkdirs()

        var bytesDownloaded = 0L
        val initialState = stateStore.markDownloading(
            source = normalized,
            bytesDownloaded = 0L,
            totalBytes = normalized.expectedByteCount,
            stagedFilePath = stagedFile.path,
            finalFilePath = finalFile.path,
            message = "Download started."
        )
        onStateChanged(initialState)

        var connection: HttpURLConnection? = null
        try {
            connection = connectionFactory(downloadUrl)
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("HTTP $responseCode ${connection.responseMessage.orEmpty()}".trim())
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0L }
                ?: normalized.expectedByteCount

            stateStore.markDownloading(
                source = normalized,
                bytesDownloaded = 0L,
                totalBytes = contentLength,
                stagedFilePath = stagedFile.path,
                finalFilePath = finalFile.path,
                message = "Downloading."
            ).also(onStateChanged)

            connection.inputStream.use { input ->
                FileOutputStream(stagedFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (cancelRequested()) {
                            throw DownloadCancelledException("Download cancelled.")
                        }

                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        stateStore.markDownloading(
                            source = normalized,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = contentLength,
                            stagedFilePath = stagedFile.path,
                            finalFilePath = finalFile.path,
                            message = "Downloading."
                        ).also(onStateChanged)
                    }
                    output.flush()
                }
            }

            stateStore.markVerifying(
                source = normalized,
                bytesDownloaded = bytesDownloaded,
                totalBytes = contentLength,
                stagedFilePath = stagedFile.path,
                finalFilePath = finalFile.path,
                message = "Verifying SHA-256."
            ).also(onStateChanged)

            val expectedSha = normalized.expectedSha256?.takeIf { it.isNotBlank() }
                ?: throw IOException("Expected SHA-256 is not configured.")

            if (!verifier.verify(stagedFile, expectedSha)) {
                cleanupBadFile(stagedFile)
                return fail(
                    source = normalized,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = contentLength,
                    stagedFilePath = stagedFile.path,
                    finalFilePath = finalFile.path,
                    message = "SHA-256 verification failed."
                ).also(onStateChanged)
            }

            moveIntoPlace(stagedFile, finalFile)

            val completedState = stateStore.markSuccess(
                source = normalized,
                bytesDownloaded = bytesDownloaded,
                totalBytes = contentLength,
                stagedFilePath = stagedFile.path,
                finalFilePath = finalFile.path,
                message = "Download completed successfully."
            )
            onStateChanged(completedState)

            registry.upsert(
                ModelManifest(
                    packId = normalized.packId,
                    version = normalized.sourceId,
                    displayName = normalized.packDisplayName,
                    state = ModelInstallState.READY,
                    installedAtEpochMs = completedState.updatedAtEpochMs,
                    files = listOf(
                        ModelFileSpec(
                            fileName = normalized.fileName,
                            relativePath = normalized.relativePath,
                            sha256 = expectedSha,
                            byteCount = bytesDownloaded
                        )
                    ),
                    notes = normalized.notes,
                    checksumSha256 = expectedSha
                ).normalized()
            )

            return completedState
        } catch (cancelled: DownloadCancelledException) {
            cleanupBadFile(stagedFile)
            return stateStore.markCancelled(
                source = normalized,
                bytesDownloaded = bytesDownloaded,
                totalBytes = resolvedTotalBytes(connection, normalized.expectedByteCount),
                stagedFilePath = stagedFile.path,
                finalFilePath = finalFile.path,
                message = cancelled.message
            ).also(onStateChanged)
        } catch (throwable: Throwable) {
            cleanupBadFile(stagedFile)
            return fail(
                source = normalized,
                bytesDownloaded = bytesDownloaded,
                totalBytes = resolvedTotalBytes(connection, normalized.expectedByteCount),
                stagedFilePath = stagedFile.path,
                finalFilePath = finalFile.path,
                message = throwable.message ?: throwable::class.simpleName ?: "Download failed."
            ).also(onStateChanged)
        } finally {
            connection?.disconnect()
        }
    }

    private fun fail(
        source: ModelDownloadSource,
        bytesDownloaded: Long = 0L,
        totalBytes: Long? = null,
        stagedFilePath: String? = null,
        finalFilePath: String? = null,
        message: String? = null
    ): ModelDownloadState {
        return stateStore.markFailed(
            source = source,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            stagedFilePath = stagedFilePath,
            finalFilePath = finalFilePath,
            message = message
        )
    }

    private fun cleanupBadFile(stagedFile: File) {
        if (stagedFile.exists()) {
            stagedFile.delete()
        }
    }

    private fun moveIntoPlace(stagedFile: File, finalFile: File) {
        try {
            Files.move(
                stagedFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                stagedFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private class DownloadCancelledException(message: String) : RuntimeException(message)

    private fun resolvedTotalBytes(
        connection: HttpURLConnection?,
        fallback: Long?
    ): Long? {
        return connection?.contentLengthLong?.takeIf { it > 0L } ?: fallback
    }
}
