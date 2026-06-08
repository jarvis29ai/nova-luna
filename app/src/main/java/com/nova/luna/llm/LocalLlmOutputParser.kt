package com.nova.luna.llm

import com.nova.luna.brain.BrainActionJsonCodec
import com.nova.luna.brain.UnifiedDomain
import com.nova.luna.model.ActionType
import com.nova.luna.model.CommandIntent
import com.nova.luna.model.IntentType
import org.json.JSONObject

class LocalLlmOutputParser {
    fun parse(rawOutput: String, request: LocalLlmRequest): LocalLlmResult {
        val trimmed = rawOutput.trim()
        val jsonStr = extractJson(trimmed) ?: return failure(rawOutput, request, LocalLlmStatus.FAILED, "Output is not valid JSON")
        
        return try {
            val json = JSONObject(jsonStr)
            val domainStr = json.optString("domain", "UNKNOWN")
            val domain = try { UnifiedDomain.valueOf(domainStr) } catch (e: Exception) { UnifiedDomain.UNKNOWN }
            
            val actionJson = json.optJSONObject("action")
            val actionTypeStr = actionJson?.optString("actionType", "UNKNOWN")
            val actionType = try { ActionType.valueOf(actionTypeStr!!) } catch (e: Exception) { ActionType.UNKNOWN }
            
            val intent = CommandIntent(
                rawText = request.commandText,
                actionType = actionType,
                entities = buildMap {
                    put("targetApp", actionJson?.optString("targetApp") ?: "")
                    put("text", actionJson?.optString("targetText") ?: "")
                    put("inputText", actionJson?.optString("inputText") ?: "")
                    put("direction", actionJson?.optString("direction") ?: "")
                }
            )

            LocalLlmResult(
                status = LocalLlmStatus.READY,
                modelId = request.modelId,
                modelDisplayName = request.modelId.name,
                rawOutput = rawOutput,
                parsedCandidateAction = intent,
                parsedDomain = domain,
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                needsClarification = json.optBoolean("requiresClarification", false),
                clarificationQuestion = json.optString("clarificationQuestion"),
                userMessage = json.optString("userMessage")
            )
        } catch (e: Exception) {
            failure(rawOutput, request, LocalLlmStatus.FAILED, "JSON parsing error: ${e.message}")
        }
    }

    private fun extractJson(text: String): String? {
        if (text.startsWith("{") && text.endsWith("}")) return text
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        return null
    }

    private fun failure(raw: String, req: LocalLlmRequest, status: LocalLlmStatus, reason: String) = LocalLlmResult(
        status = status,
        modelId = req.modelId,
        modelDisplayName = req.modelId.name,
        rawOutput = raw,
        technicalReason = reason
    )
}
