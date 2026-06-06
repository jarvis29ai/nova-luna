package com.nova.luna.content

import java.util.UUID

enum class ContentCreationCommandType {
    CREATE_PPT,
    CREATE_IMAGE,
    CREATE_VIDEO,
    CREATE_DOCUMENT,
    CREATE_EXCEL,
    CREATE_PDF,
    CREATE_OTHER,
    DETECT_BEST_FORMAT,
    EDIT_DRAFT,
    REGENERATE_DRAFT,
    FINALIZE_OUTPUT,
    EXPORT_FILE,
    SHARE_FILE,
    CANCEL,
    UNKNOWN
}

enum class ContentOutputType {
    PPT,
    IMAGE,
    VIDEO,
    DOCUMENT,
    EXCEL,
    PDF,
    OTHER,
    UNKNOWN
}

enum class ContentFlowState {
    IDLE,
    PARSING_REQUEST,
    DETECTING_OUTPUT_TYPE,
    LISTENING_REQUIREMENTS,
    ASKING_MISSING_DETAILS,
    CREATING_REQUIREMENT_PROFILE,
    EXPANDING_RAW_IDEA,
    CREATING_OUTLINE,
    CREATING_STRUCTURE,
    CREATING_KEY_POINTS,
    CREATING_DESIGN_DIRECTION,
    CREATING_DATA_SECTIONS,
    CREATING_EXPORT_PLAN,
    CHOOSING_PROMPT_EXPANSION_AI,
    GENERATING_MASTER_PROMPT,
    SELECTING_CREATION_APP,
    OPEN_SELECTED_APP,
    PASTING_OR_SENDING_PROMPT,
    GENERATING_FIRST_DRAFT,
    PROCESSING_OUTPUT_TYPE,
    CREATING_PPT_DRAFT,
    CREATING_IMAGE_PROMPT,
    CREATING_VIDEO_SCRIPT,
    CREATING_DOCUMENT_DRAFT,
    CREATING_EXCEL_DRAFT,
    CREATING_PDF_LAYOUT,
    SHOWING_FIRST_DRAFT_PREVIEW,
    WAITING_FOR_USER_REVIEW,
    COLLECTING_EDIT_FEEDBACK,
    REFINING_PROMPT,
    REGENERATING_DRAFT,
    FINALIZING_OUTPUT,
    PLANNING_EXPORT,
    EXPORTING_PPTX,
    EXPORTING_IMAGE,
    EXPORTING_VIDEO,
    EXPORTING_DOCX,
    EXPORTING_XLSX,
    EXPORTING_PDF,
    SAVING_FILE,
    SAVING_TO_PHONE,
    SAVING_TO_CLOUD,
    SHARING_FILE,
    KEEPING_EDITABLE_VERSION,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_ACTION_REQUIRED
}

enum class ContentPurpose {
    SCHOOL,
    BUSINESS,
    STARTUP,
    REPORT,
    MARKETING,
    INVESTOR_PITCH,
    PERSONAL,
    SOCIAL_MEDIA,
    UNKNOWN
}

enum class ContentStyle {
    PROFESSIONAL,
    MODERN,
    SIMPLE,
    CREATIVE,
    MINIMAL,
    FUTURISTIC,
    FORMAL,
    COLORFUL,
    UNKNOWN
}

enum class ContentQualityLevel {
    QUICK,
    STANDARD,
    HIGH_QUALITY,
    PROFESSIONAL,
    UNKNOWN
}

enum class CreationToolType {
    CHATGPT,
    CLAUDE,
    GEMINI,
    CANVA,
    CAPCUT,
    GOOGLE_DOCS,
    GOOGLE_SHEETS,
    MICROSOFT_POWERPOINT,
    MICROSOFT_WORD,
    MICROSOFT_EXCEL,
    PDF_EDITOR,
    LOCAL_INTERNAL_BUILDER,
    OTHER_INSTALLED_APP
}

enum class ContentCreationStatus {
    SUCCESS,
    PARTIAL,
    BLOCKED,
    FAILED,
    CANCELLED,
    NEEDS_USER_INPUT,
    NEEDS_CONFIRMATION,
    MANUAL_ACTION_REQUIRED
}

data class ContentRequirementProfile(
    val topic: String? = null,
    val purpose: ContentPurpose = ContentPurpose.UNKNOWN,
    val audience: String? = null,
    val style: ContentStyle = ContentStyle.UNKNOWN,
    val length: String? = null,
    val language: String? = null,
    val qualityLevel: ContentQualityLevel = ContentQualityLevel.UNKNOWN,
    val preferredTool: CreationToolType? = null,
    val exportFormat: String? = null,
    val shareTarget: String? = null
)

data class ContentBrief(
    val objective: String,
    val outline: List<String> = emptyList(),
    val structure: String? = null,
    val keyPoints: List<String> = emptyList(),
    val designDirection: String? = null,
    val sections: List<String> = emptyList(),
    val exportPlan: String? = null
)

data class ContentMasterPrompt(
    val type: ContentOutputType,
    val content: String
)

data class CreationTool(
    val id: String,
    val displayName: String,
    val packageName: String? = null,
    val supportedTypes: List<ContentOutputType>,
    val isInstalled: Boolean = false,
    val isPro: Boolean = false,
    val launchSupport: Boolean = true,
    val promptPasteSupport: Boolean = false
)

data class ContentDraft(
    val summary: String,
    val content: String,
    val previewUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ContentCreationRequest(
    val rawText: String,
    val commandType: ContentCreationCommandType = ContentCreationCommandType.UNKNOWN,
    val outputType: ContentOutputType = ContentOutputType.UNKNOWN,
    val requirements: ContentRequirementProfile = ContentRequirementProfile()
)

data class ContentCreationSession(
    val id: String = UUID.randomUUID().toString(),
    var state: ContentFlowState = ContentFlowState.IDLE,
    var request: ContentCreationRequest,
    var requirementProfile: ContentRequirementProfile = ContentRequirementProfile(),
    var brief: ContentBrief? = null,
    var masterPrompt: ContentMasterPrompt? = null,
    var selectedTool: CreationTool? = null,
    var draft: ContentDraft? = null,
    var status: ContentCreationStatus = ContentCreationStatus.NEEDS_USER_INPUT,
    var lastError: String? = null
)
