package com.nova.luna.content

import java.util.Locale

class ContentCreationIntentParser {

    fun parse(text: String): ContentCreationRequest {
        val normalized = text.lowercase(Locale.US)
        
        val commandType = detectCommandType(normalized)
        val outputType = detectOutputType(normalized, commandType)
        val requirements = extractRequirements(normalized, outputType)

        return ContentCreationRequest(
            rawText = text,
            commandType = commandType,
            outputType = outputType,
            requirements = requirements
        )
    }

    private fun detectCommandType(text: String): ContentCreationCommandType {
        val hasWriteCreationCue = Regex("""\bwrite\s+(?:a|an|the|my|this|about|for)\b""").containsMatchIn(text)

        return when {
            text.contains("finalize") || text.contains("approve") -> ContentCreationCommandType.FINALIZE_OUTPUT
            text.contains("export") || text.contains("save as") -> ContentCreationCommandType.EXPORT_FILE
            text.contains("share") -> ContentCreationCommandType.SHARE_FILE
            text.contains("regenerate") -> ContentCreationCommandType.REGENERATE_DRAFT
            text.contains("edit") || text.contains("change") || text.contains("make it") -> ContentCreationCommandType.EDIT_DRAFT
            text.contains("cancel") || text.contains("stop") -> ContentCreationCommandType.CANCEL
            text.contains("create") || text.contains("make") || text.contains("generate") || hasWriteCreationCue -> {
                when {
                    text.contains("ppt") || text.contains("presentation") || text.contains("slides") || text.contains("deck") -> ContentCreationCommandType.CREATE_PPT
                    text.contains("pdf") -> ContentCreationCommandType.CREATE_PDF
                    text.contains("image") || text.contains("poster") || text.contains("visual") || text.contains("design") || text.contains("logo") -> ContentCreationCommandType.CREATE_IMAGE
                    text.contains("video") || text.contains("reel") || text.contains("short") -> ContentCreationCommandType.CREATE_VIDEO
                    text.contains("excel") || text.contains("spreadsheet") || text.contains("table") || text.contains("tracker") -> ContentCreationCommandType.CREATE_EXCEL
                    text.contains("document") || text.contains("report") || text.contains("essay") || text.contains("article") -> ContentCreationCommandType.CREATE_DOCUMENT
                    text.contains("something") -> ContentCreationCommandType.DETECT_BEST_FORMAT
                    else -> ContentCreationCommandType.CREATE_OTHER
                }
            }
            else -> ContentCreationCommandType.UNKNOWN
        }
    }

    private fun detectOutputType(text: String, commandType: ContentCreationCommandType): ContentOutputType {
        return when (commandType) {
            ContentCreationCommandType.CREATE_PPT -> ContentOutputType.PPT
            ContentCreationCommandType.CREATE_IMAGE -> ContentOutputType.IMAGE
            ContentCreationCommandType.CREATE_VIDEO -> ContentOutputType.VIDEO
            ContentCreationCommandType.CREATE_DOCUMENT -> ContentOutputType.DOCUMENT
            ContentCreationCommandType.CREATE_EXCEL -> ContentOutputType.EXCEL
            ContentCreationCommandType.CREATE_PDF -> ContentOutputType.PDF
            else -> {
                when {
                    text.contains("ppt") || text.contains("presentation") || text.contains("slides") || text.contains("deck") -> ContentOutputType.PPT
                    text.contains("pdf") -> ContentOutputType.PDF
                    text.contains("image") || text.contains("poster") || text.contains("visual") || text.contains("design") -> ContentOutputType.IMAGE
                    text.contains("video") || text.contains("reel") || text.contains("short") -> ContentOutputType.VIDEO
                    text.contains("excel") || text.contains("spreadsheet") || text.contains("table") || text.contains("tracker") -> ContentOutputType.EXCEL
                    text.contains("document") || text.contains("report") || text.contains("essay") || text.contains("article") -> ContentOutputType.DOCUMENT
                    else -> ContentOutputType.UNKNOWN
                }
            }
        }
    }

    private fun extractRequirements(text: String, outputType: ContentOutputType): ContentRequirementProfile {
        return ContentRequirementProfile(
            topic = extractTopic(text, outputType),
            purpose = extractPurpose(text),
            audience = extractAudience(text),
            style = extractStyle(text),
            length = extractLength(text),
            language = extractLanguage(text),
            qualityLevel = extractQuality(text),
            preferredTool = extractPreferredTool(text),
            exportFormat = extractExportFormat(text),
            shareTarget = extractShareTarget(text)
        )
    }

    private fun extractTopic(text: String, outputType: ContentOutputType): String? {
        val markers = listOf("about ", "on ", "topic ", "for ")
        for (marker in markers) {
            val index = text.indexOf(marker)
            if (index != -1) {
                val rest = text.substring(index + marker.length).trim()
                if (rest.isNotEmpty() && !rest.startsWith("investor") && !rest.startsWith("student") && !rest.startsWith("customer")) return rest
            }
        }
        
        // Fallback: extract everything after the output type keyword
        val typeKeywords = when (outputType) {
            ContentOutputType.PPT -> listOf("ppt", "presentation", "slides", "deck")
            ContentOutputType.IMAGE -> listOf("image", "poster", "visual", "design", "logo")
            ContentOutputType.VIDEO -> listOf("video", "reel", "short")
            ContentOutputType.DOCUMENT -> listOf("document", "report", "essay", "article")
            ContentOutputType.EXCEL -> listOf("excel", "spreadsheet", "table", "tracker")
            ContentOutputType.PDF -> listOf("pdf")
            else -> emptyList()
        }
        
        for (keyword in typeKeywords) {
            val index = text.indexOf(keyword)
            if (index != -1) {
                val rest = text.substring(index + keyword.length).trim()
                if (rest.isNotEmpty() && !rest.startsWith("investor") && !rest.startsWith("student") && !rest.startsWith("customer")) return rest
            }
        }

        return null
    }

    private fun extractPurpose(text: String): ContentPurpose {
        return when {
            text.contains("school") || text.contains("student") || text.contains("college") -> ContentPurpose.SCHOOL
            text.contains("business") || text.contains("work") || text.contains("office") -> ContentPurpose.BUSINESS
            text.contains("startup") || text.contains("pitch") -> ContentPurpose.STARTUP
            text.contains("report") -> ContentPurpose.REPORT
            text.contains("marketing") || text.contains("ad ") || text.contains("advertise") -> ContentPurpose.MARKETING
            text.contains("investor") -> ContentPurpose.INVESTOR_PITCH
            text.contains("personal") -> ContentPurpose.PERSONAL
            text.contains("social media") || text.contains("instagram") || text.contains("facebook") -> ContentPurpose.SOCIAL_MEDIA
            else -> ContentPurpose.UNKNOWN
        }
    }

    private fun extractAudience(text: String): String? {
        val markers = listOf("for investors", "for students", "for customers", "for my team", "for kids", "for my customers")
        for (marker in markers) {
            if (text.contains(marker)) return marker.removePrefix("for ").trim()
        }
        
        // Even more flexible
        if (text.contains("for ")) {
            val afterFor = text.substringAfter("for ").trim()
            if (afterFor.contains("investor") || afterFor.contains("student") || afterFor.contains("customer") || afterFor.contains("team")) {
                return afterFor
            }
        }
        
        return when {
            text.contains("investor") -> "investors"
            text.contains("student") -> "students"
            text.contains("customer") -> "customers"
            text.contains("team") -> "team"
            else -> null
        }
    }

    private fun extractStyle(text: String): ContentStyle {
        return when {
            text.contains("professional") -> ContentStyle.PROFESSIONAL
            text.contains("modern") -> ContentStyle.MODERN
            text.contains("simple") -> ContentStyle.SIMPLE
            text.contains("creative") -> ContentStyle.CREATIVE
            text.contains("minimal") -> ContentStyle.MINIMAL
            text.contains("futuristic") -> ContentStyle.FUTURISTIC
            text.contains("formal") -> ContentStyle.FORMAL
            text.contains("colorful") -> ContentStyle.COLORFUL
            else -> ContentStyle.UNKNOWN
        }
    }

    private fun extractLength(text: String): String? {
        val regex = Regex("""(\d+)\s*(slides?|pages?|seconds?|minutes?|rows?|points?)""")
        return regex.find(text)?.value
    }

    private fun extractLanguage(text: String): String? {
        return when {
            text.contains("hindi") -> "Hindi"
            text.contains("hinglish") -> "Hinglish"
            text.contains("english") -> "English"
            text.contains("spanish") -> "Spanish"
            text.contains("french") -> "French"
            else -> null
        }
    }

    private fun extractQuality(text: String): ContentQualityLevel {
        return when {
            text.contains("quick") -> ContentQualityLevel.QUICK
            text.contains("standard") -> ContentQualityLevel.STANDARD
            text.contains("high quality") || text.contains("best quality") -> ContentQualityLevel.HIGH_QUALITY
            text.contains("professional") -> ContentQualityLevel.PROFESSIONAL
            else -> ContentQualityLevel.UNKNOWN
        }
    }

    private fun extractPreferredTool(text: String): CreationToolType? {
        return when {
            text.contains("canva") -> CreationToolType.CANVA
            text.contains("claude") -> CreationToolType.CLAUDE
            text.contains("chatgpt") -> CreationToolType.CHATGPT
            text.contains("gemini") -> CreationToolType.GEMINI
            text.contains("capcut") -> CreationToolType.CAPCUT
            text.contains("google docs") -> CreationToolType.GOOGLE_DOCS
            text.contains("google sheets") -> CreationToolType.GOOGLE_SHEETS
            text.contains("powerpoint") -> CreationToolType.MICROSOFT_POWERPOINT
            text.contains("excel") -> CreationToolType.MICROSOFT_EXCEL
            text.contains("word") -> CreationToolType.MICROSOFT_WORD
            else -> null
        }
    }

    private fun extractExportFormat(text: String): String? {
        return when {
            text.contains("pptx") -> "PPTX"
            text.contains("pdf") -> "PDF"
            text.contains("png") -> "PNG"
            text.contains("jpg") -> "JPG"
            text.contains("mp4") -> "MP4"
            text.contains("docx") -> "DOCX"
            text.contains("xlsx") -> "XLSX"
            else -> null
        }
    }

    private fun extractShareTarget(text: String): String? {
        return when {
            text.contains("whatsapp") -> "WhatsApp"
            text.contains("gmail") || text.contains("email") -> "Gmail"
            text.contains("telegram") -> "Telegram"
            else -> null
        }
    }
}
