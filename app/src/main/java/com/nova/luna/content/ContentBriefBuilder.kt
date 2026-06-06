package com.nova.luna.content

class ContentBriefBuilder {

    fun buildBrief(profile: ContentRequirementProfile, outputType: ContentOutputType): ContentBrief {
        val objective = "Create a ${profile.style} ${outputType} about ${profile.topic} for ${profile.audience} for ${profile.purpose} purposes."
        
        return when (outputType) {
            ContentOutputType.PPT -> buildPptBrief(profile, objective)
            ContentOutputType.IMAGE -> buildImageBrief(profile, objective)
            ContentOutputType.VIDEO -> buildVideoBrief(profile, objective)
            ContentOutputType.DOCUMENT -> buildDocumentBrief(profile, objective)
            ContentOutputType.EXCEL -> buildExcelBrief(profile, objective)
            ContentOutputType.PDF -> buildPdfBrief(profile, objective)
            else -> ContentBrief(objective = objective)
        }
    }

    private fun buildPptBrief(profile: ContentRequirementProfile, objective: String): ContentBrief {
        return ContentBrief(
            objective = objective,
            outline = listOf("Introduction", "Key Objectives", "Main Analysis", "Case Studies", "Conclusion", "Q&A"),
            structure = "Title Slide -> Intro -> Points -> Summary",
            keyPoints = listOf("Clear visuals", "Bullet points for readability", "High-impact data"),
            designDirection = "Clean professional layout with ${profile.style} theme",
            sections = listOf("Slide 1: Title", "Slide 2: Context", "Slide 3: Solution"),
            exportPlan = "Export as PPTX"
        )
    }

    private fun buildImageBrief(profile: ContentRequirementProfile, objective: String): ContentBrief {
        return ContentBrief(
            objective = objective,
            outline = listOf("Subject focus", "Background composition", "Text overlay if any"),
            structure = "Single frame visual",
            designDirection = "${profile.style} style, focus on ${profile.topic}",
            exportPlan = "Export as PNG"
        )
    }

    private fun buildVideoBrief(profile: ContentRequirementProfile, objective: String): ContentBrief {
        return ContentBrief(
            objective = objective,
            outline = listOf("Hook", "Core Message", "Call to action"),
            structure = "Scene-by-scene script",
            keyPoints = listOf("Fast paced", "Clear audio", "On-screen captions"),
            designDirection = "Modern transitions, ${profile.style} color grading",
            sections = listOf("Intro (0-5s)", "Body (5-25s)", "Outro (25-30s)"),
            exportPlan = "Export as MP4"
        )
    }

    private fun buildDocumentBrief(profile: ContentRequirementProfile, objective: String): ContentBrief {
        return ContentBrief(
            objective = objective,
            outline = listOf("Executive Summary", "Introduction", "Body Paragraphs", "Recommendations", "Conclusion"),
            structure = "Standard document flow",
            keyPoints = listOf("Formal tone", "Structured headings", "Evidence-based content"),
            designDirection = "Simple DOCX layout",
            sections = listOf("Header", "Introduction", "Detailed Analysis", "Conclusion"),
            exportPlan = "Export as DOCX"
        )
    }

    private fun buildExcelBrief(profile: ContentRequirementProfile, objective: String): ContentBrief {
        return ContentBrief(
            objective = objective,
            outline = listOf("Data Input", "Calculations", "Summary Chart"),
            structure = "Tabular data with formulas",
            keyPoints = listOf("Data validation", "Sum/Average formulas", "Pivot table suggestion"),
            designDirection = "Clean columns with frozen headers",
            sections = listOf("Sheet 1: Raw Data", "Sheet 2: Calculations", "Sheet 3: Dashboard"),
            exportPlan = "Export as XLSX"
        )
    }

    private fun buildPdfBrief(profile: ContentRequirementProfile, objective: String): ContentBrief {
        return ContentBrief(
            objective = objective,
            outline = listOf("Cover Page", "Content", "Back Page"),
            structure = "Fixed layout pages",
            keyPoints = listOf("High readability", "Brand consistency", "PDF-optimized formatting"),
            designDirection = "Print-ready ${profile.style} layout",
            sections = listOf("Page 1: Visual Cover", "Page 2-3: Core Content"),
            exportPlan = "Export as PDF"
        )
    }
}
