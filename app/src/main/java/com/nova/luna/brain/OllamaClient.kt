package com.nova.luna.brain

interface OllamaClient {
    fun generate(baseUrl: String, model: String, prompt: String): String
}
