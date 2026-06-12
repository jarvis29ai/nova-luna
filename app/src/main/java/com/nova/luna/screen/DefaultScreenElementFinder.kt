package com.nova.luna.screen

import java.util.Locale

class DefaultScreenElementFinder : ScreenElementFinder {

    override fun findElement(snapshot: ScreenSnapshot, query: ElementQuery): ElementMatch {
        if (!query.riskAllowed && snapshot.riskSignals.isNotEmpty()) {
            return ElementMatch(
                element = null,
                confidence = 0f,
                reason = "Screen contains risk signals: ${snapshot.riskSignals.joinToString()}",
                failureReason = "BLOCKED_BY_RISK_SIGNALS"
            )
        }

        var candidates = snapshot.elements

        if (query.packageName != null) {
            if (snapshot.packageName != query.packageName) {
                return ElementMatch(
                    element = null,
                    confidence = 0f,
                    reason = "Package mismatch. Expected ${query.packageName}, got ${snapshot.packageName}",
                    failureReason = "WRONG_APP"
                )
            }
        }

        if (query.targetType != null) {
            candidates = candidates.filter { it.type == query.targetType }
        }

        if (query.preferClickable) {
            candidates = candidates.sortedByDescending { if (it.isClickable) 1 else 0 }
        }

        if (query.preferEditable) {
            candidates = candidates.sortedByDescending { if (it.isEditable || it.type == ScreenElementType.TEXT_FIELD || it.type == ScreenElementType.SEARCH_FIELD) 1 else 0 }
        }

        val matches = candidates.map { element ->
            val score = calculateScore(element, query)
            element to score
        }.filter { it.second > 0f }.sortedByDescending { it.second }

        if (matches.isEmpty()) {
            return ElementMatch(
                element = null,
                confidence = 0f,
                reason = "No matching elements found for query.",
                failureReason = "ELEMENT_NOT_FOUND"
            )
        }

        val bestMatch = matches.first()
        val alternatives = matches.drop(1).take(3).map { it.first }

        return ElementMatch(
            element = bestMatch.first,
            confidence = bestMatch.second,
            reason = "Found element with score ${bestMatch.second}",
            alternatives = alternatives
        )
    }

    private fun calculateScore(element: ScreenElement, query: ElementQuery): Float {
        var score = 0f

        val text = element.text?.lowercase(Locale.US) ?: ""
        val desc = element.contentDescription?.lowercase(Locale.US) ?: ""
        val id = element.id?.lowercase(Locale.US) ?: ""
        val className = element.className?.lowercase(Locale.US) ?: ""

        val targetText = query.targetText?.lowercase(Locale.US)
        if (targetText != null) {
            if (text == targetText || desc == targetText) {
                score += 1.0f
            } else if (query.allowPartialMatch && (text.contains(targetText) || desc.contains(targetText))) {
                score += 0.5f
            }
        }

        val targetDesc = query.targetContentDescription?.lowercase(Locale.US)
        if (targetDesc != null) {
            if (desc == targetDesc) {
                score += 1.0f
            } else if (query.allowPartialMatch && desc.contains(targetDesc)) {
                score += 0.5f
            }
        }

        val targetId = query.resourceIdContains?.lowercase(Locale.US)
        if (targetId != null && id.contains(targetId)) {
            score += 0.8f
        }

        val targetClass = query.classNameContains?.lowercase(Locale.US)
        if (targetClass != null && className.contains(targetClass)) {
            score += 0.6f
        }

        if (query.synonyms.isNotEmpty()) {
            for (synonym in query.synonyms) {
                val synLower = synonym.lowercase(Locale.US)
                if (text.contains(synLower) || desc.contains(synLower) || id.contains(synLower)) {
                    score += 0.4f
                    break
                }
            }
        }

        if (query.preferClickable && element.isClickable) score += 0.1f
        if (query.preferEditable && (element.isEditable || element.type == ScreenElementType.TEXT_FIELD || element.type == ScreenElementType.SEARCH_FIELD)) score += 0.1f

        return score
    }
}
