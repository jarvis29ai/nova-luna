package com.nova.luna.util

import java.util.Locale

object FuzzyMatcher {
    fun normalize(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun similarity(left: String, right: String): Int {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isBlank() || b.isBlank()) return 0
        if (a == b) return 100
        if (a.contains(b) || b.contains(a)) return 92

        val distance = levenshteinDistance(a, b)
        val maxLength = maxOf(a.length, b.length).coerceAtLeast(1)
        return (100 - (distance * 100 / maxLength)).coerceIn(0, 100)
    }

    fun <T> bestMatch(
        query: String,
        candidates: List<T>,
        selector: (T) -> String,
        threshold: Int = 55
    ): T? {
        var bestCandidate: T? = null
        var bestScore = threshold

        for (candidate in candidates) {
            val score = similarity(query, selector(candidate))
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }
        return bestCandidate
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) {
                previous[j] = current[j]
            }
        }
        return previous[right.length]
    }
}

