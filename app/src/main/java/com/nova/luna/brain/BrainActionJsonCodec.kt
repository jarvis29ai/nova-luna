package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel

class BrainActionJsonCodec {
    fun encode(action: BrainAction): String {
        val paramsJson = action.params.entries.joinToString(separator = ",") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }

        return buildString {
            append('{')
            append("\"intent\":\"").append(escape(action.intent)).append("\",")
            append("\"reply\":\"").append(escape(action.reply)).append("\",")
            append("\"actionType\":\"").append(action.actionType.wireValue).append("\",")
            append("\"riskLevel\":\"").append(action.riskLevel.wireValue).append("\",")
            append("\"requiresConfirmation\":").append(action.requiresConfirmation).append(',')
            append("\"finalActionAllowed\":").append(action.finalActionAllowed).append(',')
            append("\"params\":{").append(paramsJson).append('}')
            action.nextQuestion?.takeIf { it.isNotBlank() }?.let { nextQuestion ->
                append(",\"nextQuestion\":\"").append(escape(nextQuestion)).append("\"")
            }
            append('}')
        }
    }

    fun decode(json: String): BrainAction? {
        val parser = JsonParser(json)
        val root = parser.parseObject() ?: return null

        if (!root.containsKey("intent") ||
            !root.containsKey("reply") ||
            !root.containsKey("actionType") ||
            !root.containsKey("riskLevel") ||
            !root.containsKey("requiresConfirmation") ||
            !root.containsKey("finalActionAllowed") ||
            !root.containsKey("params")
        ) {
            return null
        }

        val intent = (root["intent"] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val reply = (root["reply"] as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val actionType = BrainActionType.fromWireValue(root["actionType"] as? String) ?: return null
        val riskLevel = BrainRiskLevel.fromWireValue(root["riskLevel"] as? String) ?: return null
        val requiresConfirmation = root["requiresConfirmation"] as? Boolean ?: return null
        val finalActionAllowed = root["finalActionAllowed"] as? Boolean ?: return null
        val paramsObject = root["params"] as? Map<*, *> ?: return null
        val params = linkedMapOf<String, String>()
        for ((key, value) in paramsObject) {
            val paramKey = key?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: return null
            val paramValue = value as? String ?: return null
            params[paramKey] = paramValue
        }
        val nextQuestion = when (val value = root["nextQuestion"]) {
            null -> null
            is String -> value.takeIf { it.isNotBlank() }
            else -> return null
        }

        return BrainAction(
            intent = intent,
            reply = reply,
            actionType = actionType,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            finalActionAllowed = finalActionAllowed,
            params = params,
            nextQuestion = nextQuestion
        )
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescape(value: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\') {
                builder.append(char)
                index += 1
                continue
            }

            if (index == value.lastIndex) {
                builder.append('\\')
                break
            }

            val next = value[index + 1]
            when (next) {
                '\\' -> builder.append('\\')
                '"' -> builder.append('"')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    val hex = value.substring(index + 2, (index + 6).coerceAtMost(value.length))
                    if (hex.length == 4 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        builder.append(hex.toInt(16).toChar())
                        index += 4
                    }
                }
                else -> builder.append(next)
            }
            index += 2
        }
        return builder.toString()
    }

    private class JsonParser(private val input: String) {
        private var index: Int = 0

        fun parseObject(): Map<String, Any?>? {
            skipWhitespace()
            if (!consume('{')) return null

            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index += 1
                return result
            }

            while (index < input.length) {
                skipWhitespace()
                val key = parseString() ?: return null
                skipWhitespace()
                if (!consume(':')) return null
                skipWhitespace()
                val value = parseValue() ?: return null
                result[key] = value
                skipWhitespace()

                when (peek()) {
                    ',' -> {
                        index += 1
                        continue
                    }

                    '}' -> {
                        index += 1
                        return result
                    }

                    else -> return null
                }
            }

            return null
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '"' -> parseString()
                '{' -> parseObject()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> null
            }
        }

        private fun parseString(): String? {
            if (!consume('"')) return null
            val builder = StringBuilder()

            while (index < input.length) {
                val char = input[index]
                when (char) {
                    '\\' -> {
                        if (index + 1 >= input.length) return null
                        val next = input[index + 1]
                        when (next) {
                            '\\' -> builder.append('\\')
                            '"' -> builder.append('"')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 5 >= input.length) return null
                                val hex = input.substring(index + 2, index + 6)
                                if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
                                builder.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> builder.append(next)
                        }
                        index += 2
                    }

                    '"' -> {
                        index += 1
                        return builder.toString()
                    }

                    else -> {
                        builder.append(char)
                        index += 1
                    }
                }
            }

            return null
        }

        private fun parseBoolean(): Boolean? {
            return when {
                input.startsWith("true", index) -> {
                    index += 4
                    true
                }

                input.startsWith("false", index) -> {
                    index += 5
                    false
                }

                else -> null
            }
        }

        private fun parseNull(): Any? {
            return if (input.startsWith("null", index)) {
                index += 4
                null
            } else {
                null
            }
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) {
                index += 1
            }
        }

        private fun peek(): Char? {
            return if (index < input.length) input[index] else null
        }

        private fun consume(expected: Char): Boolean {
            if (peek() != expected) return false
            index += 1
            return true
        }
    }
}
