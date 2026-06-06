package com.nova.luna.content

class ContentToolSelector {

    fun selectTool(outputType: ContentOutputType, availableTools: List<CreationTool>): CreationTool {
        val candidates = availableTools.filter { it.isInstalled && it.supportedTypes.contains(outputType) }
        
        return when (outputType) {
            ContentOutputType.PPT -> {
                candidates.find { it.id == "claude" } 
                    ?: candidates.find { it.id == "chatgpt" }
                    ?: candidates.find { it.id == "gemini" }
                    ?: candidates.find { it.id == "canva" }
                    ?: availableTools.first { it.id == "internal" }
            }
            ContentOutputType.IMAGE -> {
                candidates.find { it.id == "canva" }
                    ?: candidates.find { it.id == "chatgpt" }
                    ?: candidates.find { it.id == "gemini" }
                    ?: availableTools.first { it.id == "internal" }
            }
            ContentOutputType.VIDEO -> {
                candidates.find { it.id == "canva" }
                    ?: candidates.find { it.id == "capcut" }
                    ?: availableTools.first { it.id == "internal" }
            }
            ContentOutputType.DOCUMENT -> {
                candidates.find { it.id == "claude" }
                    ?: candidates.find { it.id == "chatgpt" }
                    ?: candidates.find { it.id == "gemini" }
                    ?: candidates.find { it.id == "google_docs" }
                    ?: availableTools.first { it.id == "internal" }
            }
            ContentOutputType.EXCEL -> {
                candidates.find { it.id == "google_sheets" }
                    ?: candidates.find { it.id == "ms_excel" }
                    ?: candidates.find { it.id == "chatgpt" }
                    ?: availableTools.first { it.id == "internal" }
            }
            ContentOutputType.PDF -> {
                candidates.find { it.id == "google_docs" }
                    ?: candidates.find { it.id == "canva" }
                    ?: availableTools.first { it.id == "internal" }
            }
            else -> availableTools.first { it.id == "internal" }
        }
    }
}
