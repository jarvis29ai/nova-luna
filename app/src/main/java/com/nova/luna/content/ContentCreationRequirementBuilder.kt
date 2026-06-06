package com.nova.luna.content

class ContentCreationRequirementBuilder {

    fun buildProfile(request: ContentCreationRequest, existingProfile: ContentRequirementProfile): ContentRequirementProfile {
        return ContentRequirementProfile(
            topic = request.requirements.topic ?: existingProfile.topic,
            purpose = if (request.requirements.purpose != ContentPurpose.UNKNOWN) request.requirements.purpose else existingProfile.purpose,
            audience = request.requirements.audience ?: existingProfile.audience,
            style = if (request.requirements.style != ContentStyle.UNKNOWN) request.requirements.style else existingProfile.style,
            length = request.requirements.length ?: existingProfile.length,
            language = request.requirements.language ?: existingProfile.language,
            qualityLevel = if (request.requirements.qualityLevel != ContentQualityLevel.UNKNOWN) request.requirements.qualityLevel else existingProfile.qualityLevel,
            preferredTool = request.requirements.preferredTool ?: existingProfile.preferredTool,
            exportFormat = request.requirements.exportFormat ?: existingProfile.exportFormat,
            shareTarget = request.requirements.shareTarget ?: existingProfile.shareTarget
        )
    }

    fun getMissingDetails(profile: ContentRequirementProfile, outputType: ContentOutputType): List<String> {
        val missing = mutableListOf<String>()
        if (profile.topic == null) missing.add("topic")
        if (profile.purpose == ContentPurpose.UNKNOWN) missing.add("purpose")
        if (profile.audience == null) missing.add("audience")
        if (profile.style == ContentStyle.UNKNOWN) missing.add("style")
        if (profile.length == null) {
            when (outputType) {
                ContentOutputType.PPT -> missing.add("slide count")
                ContentOutputType.VIDEO -> missing.add("duration")
                ContentOutputType.DOCUMENT -> missing.add("page count")
                ContentOutputType.EXCEL -> missing.add("row/column count")
                else -> {}
            }
        }
        return missing
    }

    fun applyDefaults(profile: ContentRequirementProfile): ContentRequirementProfile {
        return profile.copy(
            style = if (profile.style == ContentStyle.UNKNOWN) ContentStyle.PROFESSIONAL else profile.style,
            language = profile.language ?: "English",
            qualityLevel = if (profile.qualityLevel == ContentQualityLevel.UNKNOWN) ContentQualityLevel.STANDARD else profile.qualityLevel
        )
    }
}
