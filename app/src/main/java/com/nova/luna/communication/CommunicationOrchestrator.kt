package com.nova.luna.communication

import android.content.Context
import java.util.UUID

class CommunicationOrchestrator(private val context: Context) {
    private val parser = CommunicationIntentParser()
    private val permissionChecker = CommunicationPermissionChecker(context)
    private val sourceRegistry = CommunicationSourceRegistry(context)
    private val reader = CommunicationMessageReader(context)
    private val ranker = CommunicationImportanceRanker()
    private val summarizer = CommunicationSummarizer()
    private val searchEngine = CommunicationSearchEngine(reader)
    private val replyDraftModel = CommunicationReplyDraftModel()
    private val emailDraftModel = CommunicationEmailDraftModel()
    private val sendExecutor = CommunicationSendExecutor(context)
    private val voiceResponses = CommunicationVoiceResponses()
    private val logger = CommunicationLogger()

    private var activeSession: CommunicationSession? = null

    fun isActive(): Boolean {
        val session = activeSession ?: return false
        return session.state != CommunicationFlowState.COMPLETED &&
               session.state != CommunicationFlowState.FAILED &&
               session.state != CommunicationFlowState.CANCELLED &&
               session.state != CommunicationFlowState.IDLE &&
               session.state != CommunicationFlowState.PERMISSION_BLOCKED
    }

    fun handleRequest(text: String): CommunicationFinalSummary {
        val currentSession = activeSession
        if (currentSession != null && isActive()) {
            val request = parser.parse(text)
            currentSession.request = request
            logger.logAction("Continuing active session: ${currentSession.id} with command: ${request.commandType}")
            return processSession(currentSession)
        }

        val request = parser.parse(text)
        val session = CommunicationSession(
            id = UUID.randomUUID().toString(),
            request = request,
            state = CommunicationFlowState.PARSING_REQUEST
        )
        activeSession = session
        logger.logState(session.state)

        return processSession(session)
    }

    private fun processSession(session: CommunicationSession): CommunicationFinalSummary {
        return when (session.request.commandType) {
            CommunicationCommandType.SUMMARIZE_ALL_TODAY -> handleSummarizeAll(session)
            CommunicationCommandType.SUMMARIZE_ONE_PLATFORM -> handleSummarizePlatform(session)
            CommunicationCommandType.SUMMARIZE_SINGLE_LONG_MESSAGE -> handleSummarizeSingleMessage(session)
            CommunicationCommandType.FIND_MESSAGE -> handleSearch(session)
            CommunicationCommandType.DRAFT_REPLY -> handleDraftReply(session)
            CommunicationCommandType.DRAFT_EMAIL -> handleDraftEmail(session)
            CommunicationCommandType.SEND_REPLY, CommunicationCommandType.SEND_EMAIL -> handleSend(session)
            CommunicationCommandType.CANCEL -> handleCancel(session)
            else -> voiceResponses.getErrorResponse("Unknown command type")
        }
    }

    private fun handleSummarizeAll(session: CommunicationSession): CommunicationFinalSummary {
        session.state = CommunicationFlowState.CHECKING_PERMISSIONS
        logger.logState(session.state)

        val sources = sourceRegistry.getAvailableSources().filter { it.isInstalled }
        val messages = mutableListOf<CommunicationMessage>()

        for (source in sources) {
            val permStatus = permissionChecker.checkPermission(source.platform)
            if (permStatus == CommunicationPermissionStatus.GRANTED) {
                val platformMessages = reader.readMessages(source.platform)
                val rankedMessages = platformMessages.map { 
                    it.copy(
                        importance = ranker.rank(it),
                        isSensitive = ranker.isSensitive(it)
                    )
                }
                messages.addAll(rankedMessages)
            }
        }

        session.messages.clear()
        session.messages.addAll(messages)
        val summary = summarizer.summarizeAll(messages)
        session.state = CommunicationFlowState.COMPLETED
        logger.logState(session.state)

        return voiceResponses.getSummaryResponse(summary)
    }

    private fun handleSummarizePlatform(session: CommunicationSession): CommunicationFinalSummary {
        val platform = session.request.targetPlatform ?: return voiceResponses.getErrorResponse("Platform not specified")
        
        session.state = CommunicationFlowState.CHECKING_PERMISSIONS
        logger.logState(session.state)

        val permStatus = permissionChecker.checkPermission(platform)
        if (permStatus != CommunicationPermissionStatus.GRANTED) {
            return voiceResponses.getBlockedResponse(permStatus)
        }

        val messages = reader.readMessages(platform)
        val rankedMessages = messages.map { 
            it.copy(
                importance = ranker.rank(it),
                isSensitive = ranker.isSensitive(it)
            )
        }
        
        val summary = summarizer.summarizePlatform(platform, rankedMessages)
        session.state = CommunicationFlowState.COMPLETED
        logger.logState(session.state)

        return voiceResponses.getSummaryResponse(CommunicationSummary(listOf(summary), emptyList(), summary.summaryText))
    }

    private fun handleSummarizeSingleMessage(session: CommunicationSession): CommunicationFinalSummary {
        // Find message logic
        session.state = CommunicationFlowState.SUMMARIZING_LONG_MESSAGE
        logger.logState(session.state)
        
        return voiceResponses.getErrorResponse("Single message summarization not fully implemented")
    }

    private fun handleSearch(session: CommunicationSession): CommunicationFinalSummary {
        session.state = CommunicationFlowState.SEARCHING_ALLOWED_PLATFORMS
        logger.logState(session.state)

        val result = searchEngine.search(session.request)
        session.state = CommunicationFlowState.SHOWING_SEARCH_MATCHES
        logger.logState(session.state)

        return voiceResponses.getSearchResponse(result)
    }

    private fun handleDraftReply(session: CommunicationSession): CommunicationFinalSummary {
        session.state = CommunicationFlowState.CREATING_DRAFT
        logger.logState(session.state)

        val draft = replyDraftModel.createDraft(
            CommunicationMessage(
                id = "dummy",
                platform = session.request.targetPlatform ?: CommunicationPlatform.SMS,
                sender = CommunicationSender(session.request.senderName ?: "Unknown"),
                timestamp = java.time.LocalDateTime.now(),
                content = ""
            ),
            session.request.draftInstruction ?: "",
            session.request.tone ?: DraftTone.USER_STYLE
        )
        session.currentDraft = draft
        session.state = CommunicationFlowState.SHOWING_DRAFT_TO_USER
        logger.logState(session.state)

        return voiceResponses.getDraftCreatedResponse(draft)
    }

    private fun handleDraftEmail(session: CommunicationSession): CommunicationFinalSummary {
        session.state = CommunicationFlowState.CREATING_DRAFT
        logger.logState(session.state)

        val draft = emailDraftModel.createDraft(
            session.request.draftInstruction ?: "",
            session.request.tone ?: DraftTone.FORMAL
        )
        session.currentDraft = draft
        session.state = CommunicationFlowState.SHOWING_DRAFT_TO_USER
        logger.logState(session.state)

        return voiceResponses.getDraftCreatedResponse(draft)
    }

    private fun handleSend(session: CommunicationSession): CommunicationFinalSummary {
        val draft = session.currentDraft ?: return voiceResponses.getErrorResponse("No draft to send")
        
        session.state = CommunicationFlowState.SENDING_MESSAGE_OR_EMAIL
        logger.logState(session.state)

        val status = when (draft) {
            is ReplyDraft -> sendExecutor.sendReply(draft)
            is EmailDraft -> sendExecutor.sendEmail(draft)
            else -> CommunicationStatus.FAILED
        }

        session.status = status
        
        if (status == CommunicationStatus.BLOCKED) {
            session.state = CommunicationFlowState.PERMISSION_BLOCKED
            logger.logState(session.state)
            return voiceResponses.getBlockedResponse(CommunicationPermissionStatus.BLOCKED_BY_ACCESSIBILITY_NOT_READY)
        }

        session.state = if (status == CommunicationStatus.SUCCESS) CommunicationFlowState.COMPLETED else CommunicationFlowState.FAILED
        logger.logState(session.state)

        return CommunicationFinalSummary(
            popupText = if (status == CommunicationStatus.SUCCESS) "Sent successfully." else "Failed to send.",
            voiceText = if (status == CommunicationStatus.SUCCESS) "I've sent it." else "I couldn't send the message.",
            status = status
        )
    }

    private fun handleCancel(session: CommunicationSession): CommunicationFinalSummary {
        session.state = CommunicationFlowState.CANCELLED
        logger.logState(session.state)
        
        return voiceResponses.getCancelResponse()
    }
}
