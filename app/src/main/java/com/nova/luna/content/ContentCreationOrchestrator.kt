package com.nova.luna.content

import android.content.Context

class ContentCreationOrchestrator(private val context: Context) {
    private val parser = ContentCreationIntentParser()
    private val requirementBuilder = ContentCreationRequirementBuilder()
    private val briefBuilder = ContentBriefBuilder()
    private val promptBuilder = ContentPromptBuilder()
    private val toolRegistry = ContentToolRegistry(context)
    private val toolSelector = ContentToolSelector()
    private val appLauncher = ContentAppLauncher(context)
    private val draftGenerator = ContentDraftGenerator()
    private val reviewManager = ContentReviewManager()
    private val exportPlanner = ContentExportPlanner()
    private val fileManager = ContentFileManager(context)
    private val shareManager = ContentShareManager(context)
    private val voiceResponses = ContentCreationVoiceResponses()
    private val logger = ContentCreationLogger()

    private var activeSession: ContentCreationSession? = null

    fun handleRequest(text: String): ContentCreationOrchestratorResult {
        val request = parser.parse(text)
        
        // If it's a new creation request, start/restart session
        if (isNewCreationRequest(request)) {
            activeSession = startNewSession(text)
        }
        
        val session = activeSession ?: return ContentCreationOrchestratorResult(
            popupText = "I'm not sure what you want to create. Try 'Create a PPT' or 'Make an image'.",
            voiceText = "I don't have an active creation session.",
            status = ContentCreationStatus.FAILED
        )

        // Handle global commands like CANCEL
        if (request.commandType == ContentCreationCommandType.CANCEL) {
            activeSession = null
            return ContentCreationOrchestratorResult(
                popupText = "Cancelled.",
                voiceText = "Okay, cancelled.",
                status = ContentCreationStatus.CANCELLED
            )
        }

        // Handle completion requests regardless of current state if we have a draft or enough info
        if (request.commandType == ContentCreationCommandType.SHARE_FILE || request.commandType == ContentCreationCommandType.EXPORT_FILE) {
            if (session.state == ContentFlowState.COMPLETED || session.state == ContentFlowState.WAITING_FOR_USER_REVIEW || session.state == ContentFlowState.SHARING_FILE) {
                return handlePostCompletionRequest(session, text)
            }
        }

        return when (session.state) {
            ContentFlowState.PARSING_REQUEST -> processInitialRequest(session, text)
            ContentFlowState.ASKING_MISSING_DETAILS -> handleUserReply(session, text)
            ContentFlowState.WAITING_FOR_USER_REVIEW -> handleReviewFeedback(session, text)
            ContentFlowState.SHARING_FILE -> handleSharingConfirmation(session, text)
            ContentFlowState.COMPLETED -> handlePostCompletionRequest(session, text)
            else -> processInitialRequest(session, text)
        }
    }

    private fun isNewCreationRequest(request: ContentCreationRequest): Boolean {
        return request.commandType in listOf(
            ContentCreationCommandType.CREATE_PPT,
            ContentCreationCommandType.CREATE_IMAGE,
            ContentCreationCommandType.CREATE_VIDEO,
            ContentCreationCommandType.CREATE_DOCUMENT,
            ContentCreationCommandType.CREATE_EXCEL,
            ContentCreationCommandType.CREATE_PDF,
            ContentCreationCommandType.CREATE_OTHER,
            ContentCreationCommandType.DETECT_BEST_FORMAT
        )
    }

    private fun startNewSession(text: String): ContentCreationSession {
        val request = parser.parse(text)
        return ContentCreationSession(
            state = ContentFlowState.PARSING_REQUEST,
            request = request
        )
    }

    private fun processInitialRequest(session: ContentCreationSession, text: String): ContentCreationOrchestratorResult {
        val request = parser.parse(text)
        session.request = request
        session.requirementProfile = requirementBuilder.buildProfile(request, session.requirementProfile)

        val missing = requirementBuilder.getMissingDetails(session.requirementProfile, request.outputType)
        if (missing.isNotEmpty()) {
            session.state = ContentFlowState.ASKING_MISSING_DETAILS
            val nextMissing = missing.first()
            return ContentCreationOrchestratorResult(
                popupText = "I need more details about the $nextMissing.",
                voiceText = voiceResponses.getMissingDetailQuestion(nextMissing),
                status = ContentCreationStatus.NEEDS_USER_INPUT
            )
        }

        return proceedToCreation(session)
    }

    private fun handleUserReply(session: ContentCreationSession, text: String): ContentCreationOrchestratorResult {
        val missingBefore = requirementBuilder.getMissingDetails(session.requirementProfile, session.request.outputType)
        val currentlyAskingFor = if (missingBefore.isNotEmpty()) missingBefore.first() else null

        val updateRequest = parser.parse(text)
        
        if (updateRequest.commandType == ContentCreationCommandType.SHARE_FILE || updateRequest.commandType == ContentCreationCommandType.EXPORT_FILE) {
            return handlePostCompletionRequest(session, text)
        }

        var newProfile = requirementBuilder.buildProfile(updateRequest, session.requirementProfile)
        
        // If the parser didn't find the specific detail we were asking for, but the user gave a direct answer
        if (currentlyAskingFor != null) {
            newProfile = when (currentlyAskingFor) {
                "topic" -> if (newProfile.topic == null) newProfile.copy(topic = text) else newProfile
                "purpose" -> if (newProfile.purpose == ContentPurpose.UNKNOWN) newProfile.copy(purpose = mapToPurpose(text)) else newProfile
                "audience" -> if (newProfile.audience == null) newProfile.copy(audience = text) else newProfile
                "style" -> if (newProfile.style == ContentStyle.UNKNOWN) newProfile.copy(style = mapToStyle(text)) else newProfile
                "slide count", "duration", "page count", "row/column count" -> if (newProfile.length == null) newProfile.copy(length = text) else newProfile
                else -> newProfile
            }
        }
        
        session.requirementProfile = newProfile

        val missingAfter = requirementBuilder.getMissingDetails(session.requirementProfile, session.request.outputType)
        if (missingAfter.isNotEmpty()) {
            val nextMissing = missingAfter.first()
            return ContentCreationOrchestratorResult(
                popupText = "Thanks. What about the $nextMissing?",
                voiceText = voiceResponses.getMissingDetailQuestion(nextMissing),
                status = ContentCreationStatus.NEEDS_USER_INPUT
            )
        }

        return proceedToCreation(session)
    }

    private fun mapToPurpose(text: String): ContentPurpose {
        val normalized = text.lowercase()
        return when {
            normalized.contains("school") || normalized.contains("student") -> ContentPurpose.SCHOOL
            normalized.contains("business") || normalized.contains("work") -> ContentPurpose.BUSINESS
            normalized.contains("startup") || normalized.contains("pitch") -> ContentPurpose.STARTUP
            normalized.contains("personal") -> ContentPurpose.PERSONAL
            normalized.contains("social") -> ContentPurpose.SOCIAL_MEDIA
            else -> ContentPurpose.UNKNOWN
        }
    }

    private fun mapToStyle(text: String): ContentStyle {
        val normalized = text.lowercase()
        return when {
            normalized.contains("professional") -> ContentStyle.PROFESSIONAL
            normalized.contains("modern") -> ContentStyle.MODERN
            normalized.contains("simple") -> ContentStyle.SIMPLE
            normalized.contains("creative") -> ContentStyle.CREATIVE
            else -> ContentStyle.UNKNOWN
        }
    }

    private fun proceedToCreation(session: ContentCreationSession): ContentCreationOrchestratorResult {
        session.requirementProfile = requirementBuilder.applyDefaults(session.requirementProfile)
        session.state = ContentFlowState.EXPANDING_RAW_IDEA
        
        val brief = briefBuilder.buildBrief(session.requirementProfile, session.request.outputType)
        session.brief = brief
        
        val tools = toolRegistry.getAvailableTools()
        val selectedTool = toolSelector.selectTool(session.request.outputType, tools)
        session.selectedTool = selectedTool
        
        val toolType = runCatching { CreationToolType.valueOf(selectedTool.id.uppercase()) }.getOrNull()
        val masterPrompt = promptBuilder.buildMasterPrompt(session.request.outputType, session.requirementProfile, brief, toolType)
        session.masterPrompt = masterPrompt
        
        val draft = draftGenerator.generateDraft(session.request.outputType, brief)
        session.draft = draft
        
        session.state = ContentFlowState.WAITING_FOR_USER_REVIEW
        
        return ContentCreationOrchestratorResult(
            popupText = "I've prepared a draft for your ${session.request.outputType}.\n\nSUMMARY: ${draft.summary}\n\nTool suggested: ${selectedTool.displayName}",
            voiceText = voiceResponses.getDraftReadyResponse(session.request.outputType),
            status = ContentCreationStatus.NEEDS_CONFIRMATION
        )
    }

    private fun handleReviewFeedback(session: ContentCreationSession, text: String): ContentCreationOrchestratorResult {
        if (reviewManager.isApproval(text)) {
            return finalizeCreation(session)
        }
        
        if (reviewManager.isCancel(text)) {
            activeSession = null
            return ContentCreationOrchestratorResult(
                popupText = "Cancelled.",
                voiceText = "Okay, cancelled.",
                status = ContentCreationStatus.CANCELLED
            )
        }
        
        // Handle feedback
        val newBrief = reviewManager.processFeedback(text, session.brief!!)
        session.brief = newBrief
        
        // Regenerate draft
        val newDraft = draftGenerator.generateDraft(session.request.outputType, newBrief)
        session.draft = newDraft
        
        return ContentCreationOrchestratorResult(
            popupText = "Updated draft summary:\n${newDraft.summary}\n\nDoes this look better?",
            voiceText = "I've updated the draft. How is it now?",
            status = ContentCreationStatus.NEEDS_CONFIRMATION
        )
    }

    private fun finalizeCreation(session: ContentCreationSession): ContentCreationOrchestratorResult {
        session.state = ContentFlowState.FINALIZING_OUTPUT
        
        val tool = session.selectedTool!!
        var manualAction = false
        
        if (tool.id != "internal") {
            val launched = appLauncher.launch(tool)
            if (launched) {
                session.status = ContentCreationStatus.MANUAL_ACTION_REQUIRED
                manualAction = true
            }
        }
        
        if (!manualAction) {
            val exportPlan = exportPlanner.planExport(session.request.outputType, session.requirementProfile.exportFormat)
            val file = fileManager.saveDraftToFile(session.draft!!, "content_${session.id.substring(0,8)}.txt")
            session.status = ContentCreationStatus.SUCCESS
        }

        session.state = ContentFlowState.COMPLETED
        return ContentCreationOrchestratorResult(
            popupText = voiceResponses.getFinalResponse(session.status, tool.displayName) + "\n\nYou can say 'Share on WhatsApp' or 'Export as PDF'.",
            voiceText = voiceResponses.getFinalResponse(session.status, tool.displayName),
            status = session.status
        )
    }

    private fun handlePostCompletionRequest(session: ContentCreationSession, text: String): ContentCreationOrchestratorResult {
        val request = parser.parse(text)
        return when (request.commandType) {
            ContentCreationCommandType.SHARE_FILE -> {
                val target = request.requirements.shareTarget ?: "WhatsApp"
                session.state = ContentFlowState.SHARING_FILE
                session.requirementProfile = session.requirementProfile.copy(shareTarget = target)
                ContentCreationOrchestratorResult(
                    popupText = "Do you want me to share this on $target?",
                    voiceText = "Should I share this on $target?",
                    status = ContentCreationStatus.NEEDS_CONFIRMATION
                )
            }
            ContentCreationCommandType.EXPORT_FILE -> {
                val format = request.requirements.exportFormat
                val plan = exportPlanner.planExport(session.request.outputType, format)
                ContentCreationOrchestratorResult(
                    popupText = "$plan. I've saved a copy on your phone.",
                    voiceText = "I've exported the file as requested.",
                    status = ContentCreationStatus.SUCCESS
                )
            }
            else -> {
                ContentCreationOrchestratorResult(
                    popupText = "Creation complete. You can say 'Share on WhatsApp' or 'Export as PDF'.",
                    voiceText = "What else should I do with the file?",
                    status = ContentCreationStatus.SUCCESS
                )
            }
        }
    }

    private fun handleSharingConfirmation(session: ContentCreationSession, text: String): ContentCreationOrchestratorResult {
        if (reviewManager.isApproval(text)) {
            val target = session.requirementProfile.shareTarget
            val success = shareManager.shareContent(session.draft?.content ?: "", target)
            session.state = ContentFlowState.COMPLETED
            return ContentCreationOrchestratorResult(
                popupText = if (success) "Shared successfully." else "Failed to open share sheet.",
                voiceText = if (success) "I've opened the share sheet." else "I couldn't share the file.",
                status = if (success) ContentCreationStatus.SUCCESS else ContentCreationStatus.FAILED
            )
        } else {
            session.state = ContentFlowState.COMPLETED
            return ContentCreationOrchestratorResult(
                popupText = "Sharing cancelled.",
                voiceText = "Okay, I won't share it.",
                status = ContentCreationStatus.SUCCESS
            )
        }
    }

    fun isActive(): Boolean = activeSession != null
}

data class ContentCreationOrchestratorResult(
    val popupText: String,
    val voiceText: String,
    val status: ContentCreationStatus = ContentCreationStatus.SUCCESS
)
