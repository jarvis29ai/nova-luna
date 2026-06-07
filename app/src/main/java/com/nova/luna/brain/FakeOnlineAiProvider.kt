package com.nova.luna.brain

class FakeOnlineAiProvider(
    private val response: String = """{"intent":"online_help","reply":"Here is a safe draft.","actionType":"read_only","riskLevel":"safe","requiresConfirmation":false,"finalActionAllowed":false,"params":{"source":"fake"}}""",
    override val providerType: OnlineAiProviderType = OnlineAiProviderType.FAKE,
    private val providerName: String = "FakeOnlineAiProvider"
) : OnlineAiProvider {
    override val available: Boolean = true

    override fun generate(prompt: String, timeoutMs: Long): OnlineAiResult {
        return OnlineAiResult(
            providerType = providerType,
            status = OnlineAiStatus.READY,
            available = true,
            rawResponse = response,
            reason = "Fake online AI provider returned a deterministic response.",
            promptBuilt = true,
            providerName = providerName,
            latencyMillis = 0L
        )
    }

    override fun diagnostics(): String {
        return "$providerName(${providerType.wireValue})"
    }
}
