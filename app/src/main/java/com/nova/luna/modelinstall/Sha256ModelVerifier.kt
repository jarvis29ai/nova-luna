package com.nova.luna.modelinstall

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class Sha256ModelVerifier {
    fun digestHex(file: File): String {
        require(file.exists()) { "File does not exist: ${file.path}" }
        require(file.isFile) { "Path is not a file: ${file.path}" }

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun verify(file: File, expectedSha256: String?): Boolean {
        val normalizedExpected = expectedSha256?.trim()?.lowercase()
            ?: return false
        if (normalizedExpected.isBlank()) return false
        return digestHex(file).equals(normalizedExpected, ignoreCase = true)
    }
}
