package com.nova.luna.modelinstall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class Sha256ModelVerifierTest {
    private val verifier = Sha256ModelVerifier()

    @Test
    fun passesForCorrectHash() {
        val file = tempFile("hello, sha256")
        val expected = sha256Hex(file.readBytes())

        assertTrue(verifier.verify(file, expected))
    }

    @Test
    fun failsForWrongHash() {
        val file = tempFile("hello, sha256")

        assertFalse(verifier.verify(file, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    private fun tempFile(text: String): File {
        val file = Files.createTempFile("nova_luna_sha256_test", ".bin").toFile()
        file.writeText(text)
        return file
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
