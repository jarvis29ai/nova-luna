package com.nova.luna.brain

class LocalMockBrainProvider(
    private val interpreter: LocalBrainInterpreter = LocalBrainInterpreter(),
    private val codec: BrainActionJsonCodec = BrainActionJsonCodec()
) : BrainProvider, BrainProviderDiagnostics {
    override fun analyze(request: BrainRequest): String {
        return codec.encode(interpreter.interpret(request))
    }

    override fun diagnose(request: BrainRequest): BrainProviderTrace {
        val action = interpreter.interpret(request)
        val response = codec.encode(action)
        return BrainProviderTrace(
            providerName = this::class.java.simpleName,
            rawResponse = response,
            extractedJson = response,
            parsedAction = action
        )
    }
}
