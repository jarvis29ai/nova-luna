package com.nova.luna.content

class ContentDraftGenerator {

    fun generateDraft(outputType: ContentOutputType, brief: ContentBrief): ContentDraft {
        val summary = "Draft for ${outputType} on ${brief.objective.substringAfter("about ").substringBefore(" for")}"
        val content = buildString {
            append("--- ${outputType} DRAFT ---\n\n")
            append("OBJECTIVE: ${brief.objective}\n\n")
            append("OUTLINE:\n")
            brief.outline.forEach { append("- $it\n") }
            append("\nSTRUCTURE: ${brief.structure}\n")
            append("\nKEY POINTS:\n")
            brief.keyPoints.forEach { append("- $it\n") }
            append("\nDESIGN DIRECTION: ${brief.designDirection}\n")
            append("\n--- END OF DRAFT ---")
        }

        return ContentDraft(
            summary = summary,
            content = content,
            metadata = mapOf("generatedBy" to "Luna/Nova Internal")
        )
    }
}
