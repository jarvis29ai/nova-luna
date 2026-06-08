package com.nova.luna.brain

import com.nova.luna.content.ContentCreationIntentParser
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType

class ContentHandler(
    private val parser: ContentCreationIntentParser = ContentCreationIntentParser()
) : DomainHandler {
    override val domain: UnifiedDomain = UnifiedDomain.CONTENT
    override val modelName: String = "ContentParser"

    override fun canHandle(command: String, context: AssistantContext?): DomainMatch {
        val signals = mutableListOf<String>()
        val creationVerbs = listOf("create", "make", "generate", "draft", "build", "design")
        val formats = listOf("ppt", "presentation", "spreadsheet", "excel", "image", "video", "document", "pdf")

        for (verb in creationVerbs) {
            if (command.contains(verb)) signals.add(verb)
        }
        for (format in formats) {
            if (command.contains(format)) signals.add(format)
        }

        val request = parser.parse(command)
        val hasParserResult = request.commandType != com.nova.luna.content.ContentCreationCommandType.UNKNOWN

        val confidence = if (signals.size >= 2) 0.95f 
                         else if (signals.size == 1) 0.75f
                         else if (hasParserResult) 0.85f
                         else 0.0f
        
        return DomainMatch(
            domain = domain,
            confidence = confidence,
            matchedSignals = signals,
            reason = "Content creation keywords or parser match found"
        )
    }

    override fun parse(command: String, context: AssistantContext?): CommandIntent {
        return CommandIntent(
            rawText = command,
            normalizedText = com.nova.luna.util.AssistantTextNormalizer.normalize(command),
            intentType = IntentType.CONTENT_CREATION,
            actionType = ActionType.CONTENT_CREATION
        )
    }
}
