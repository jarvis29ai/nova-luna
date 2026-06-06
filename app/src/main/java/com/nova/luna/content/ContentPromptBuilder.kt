package com.nova.luna.content

class ContentPromptBuilder {

    fun buildMasterPrompt(
        outputType: ContentOutputType,
        profile: ContentRequirementProfile,
        brief: ContentBrief,
        toolType: CreationToolType?
    ): ContentMasterPrompt {
        val basePrompt = StringBuilder()
        basePrompt.append("TASK: Create a ${outputType} on the topic: '${profile.topic}'.\n")
        basePrompt.append("PURPOSE: ${profile.purpose}\n")
        basePrompt.append("AUDIENCE: ${profile.audience}\n")
        basePrompt.append("STYLE: ${profile.style}\n")
        basePrompt.append("LENGTH: ${profile.length}\n")
        basePrompt.append("LANGUAGE: ${profile.language}\n")
        basePrompt.append("\nOBJECTIVE: ${brief.objective}\n")
        basePrompt.append("STRUCTURE:\n${brief.structure}\n")
        basePrompt.append("\nOUTLINE:\n")
        brief.outline.forEach { basePrompt.append("- $it\n") }
        basePrompt.append("\nKEY POINTS:\n")
        brief.keyPoints.forEach { basePrompt.append("- $it\n") }
        basePrompt.append("\nDESIGN DIRECTION: ${brief.designDirection}\n")
        
        if (toolType != null) {
            basePrompt.append("\nOPTIMIZE FOR TOOL: $toolType\n")
        }
        
        basePrompt.append("\nIMPORTANT: Please provide editable content and clear instructions for finalization.")

        return ContentMasterPrompt(
            type = outputType,
            content = basePrompt.toString()
        )
    }
}
