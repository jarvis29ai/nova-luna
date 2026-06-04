package com.nova.luna.brain

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class HttpOllamaClient(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000
) : OllamaClient {
    override fun generate(baseUrl: String, model: String, prompt: String): String {
        val endpoint = URL(baseUrl.trimEnd('/') + "/api/generate")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        val payload = buildRequestBody(model, prompt)
        connection.outputStream.use { output ->
            output.write(payload.toByteArray(StandardCharsets.UTF_8))
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

        if (code !in 200..299) {
            throw IOException("Ollama request failed with HTTP $code: ${response.take(200)}")
        }

        return response
    }

    private fun buildRequestBody(model: String, prompt: String): String {
        return buildString {
            append('{')
            append("\"model\":\"").append(escapeJson(model)).append("\",")
            append("\"prompt\":\"").append(escapeJson(prompt)).append("\",")
            append("\"stream\":false,")
            append("\"format\":\"json\"")
            append('}')
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
