package com.nova.luna.communication

class CommunicationSearchEngine(private val reader: CommunicationMessageReader) {

    fun search(request: CommunicationRequest): MessageSearchResult {
        val query = request.searchQuery ?: return MessageSearchResult(emptyList(), 0)
        val platform = request.targetPlatform
        
        val messages = reader.searchMessages(query, platform)
        val matches = messages.map { message ->
            MessageMatch(
                message = message,
                snippet = createSnippet(message, query),
                score = calculateScore(message, query)
            )
        }.sortedByDescending { it.score }

        return MessageSearchResult(matches, matches.size)
    }

    private fun createSnippet(message: CommunicationMessage, query: String): String {
        val content = message.content
        val index = content.lowercase().indexOf(query.lowercase())
        return if (index != -1) {
            val start = maxOf(0, index - 30)
            val end = minOf(content.length, index + query.length + 30)
            "...${content.substring(start, end)}..."
        } else {
            content.take(60)
        }
    }

    private fun calculateScore(message: CommunicationMessage, query: String): Float {
        var score = 0f
        val lowerContent = message.content.lowercase()
        val lowerQuery = query.lowercase()
        
        if (lowerContent.contains(lowerQuery)) score += 10f
        if (message.sender.name.lowercase().contains(lowerQuery)) score += 5f
        if (message.subject?.lowercase()?.contains(lowerQuery) == true) score += 8f
        
        return score
    }
}
