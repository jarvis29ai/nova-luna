package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel

class BrainActionJsonCodec {
    fun encode(action: BrainAction): String {
        val paramsJson = action.params.entries.joinToString(separator = ",") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }

        val errorsJson = action.errors.joinToString(separator = ",") { "\"${escape(it)}\"" }

        return buildString {
            append('{')
            append("\"schemaVersion\":").append(action.schemaVersion).append(",")
            append("\"source\":\"").append(action.source.name).append("\",")
            append("\"rawCommand\":\"").append(escape(action.rawCommand)).append("\",")
            append("\"normalizedCommand\":\"").append(escape(action.normalizedCommand)).append("\",")
            append("\"intent\":\"").append(escape(action.intent)).append("\",")
            append("\"actionType\":\"").append(action.actionType.name).append("\",")
            append("\"riskLevel\":\"").append(action.riskLevel.name).append("\",")
            append("\"requiresConfirmation\":").append(action.requiresConfirmation).append(",")
            append("\"params\":{").append(paramsJson).append("},")
            append("\"confidence\":").append(action.confidence).append(",")
            append("\"language\":\"").append(escape(action.language)).append("\",")
            append("\"assistantReply\":\"").append(escape(action.assistantReply)).append("\",")
            append("\"reason\":\"").append(escape(action.reason)).append("\",")
            append("\"errors\":[").append(errorsJson).append("]")
            append('}')
        }
    }

    fun decode(json: String): BrainAction? {
        val parser = JsonParser(json)
        val root = parser.parseObject() ?: return null

        val intent = root.jsonString("intent") ?: return null
        val actionType = BrainActionType.entries.find { it.name == root.jsonString("actionType") } ?: BrainActionType.UNKNOWN
        val riskLevel = BrainRiskLevel.entries.find { it.name == root.jsonString("riskLevel") } ?: BrainRiskLevel.UNKNOWN
        val requiresConfirmation = root.jsonBoolean("requiresConfirmation", false)
        
        val params = linkedMapOf<String, String>()
        val paramsObj = root["params"] as? Map<*, *>
        paramsObj?.forEach { (k, v) ->
            if (k is String && v is String) params[k] = v
        }

        val confidence = root.jsonDouble("confidence", 0.0)
        val assistantReply = root.jsonString("assistantReply") ?: root.jsonString("reply") ?: ""
        val reason = root.jsonString("reason") ?: ""
        val language = root.jsonString("language") ?: "unknown"
        val source = runCatching { BrainActionSource.valueOf(root.jsonString("source") ?: "MODEL") }.getOrDefault(BrainActionSource.MODEL)
        val rawCommand = root.jsonString("rawCommand") ?: ""
        val normalizedCommand = root.jsonString("normalizedCommand") ?: ""
        val schemaVersion = root.jsonInt("schemaVersion", 1)
        
        val errors = mutableListOf<String>()
        val errorsList = root["errors"] as? List<*>
        errorsList?.forEach { if (it is String) errors.add(it) }

        return BrainAction(
            schemaVersion = schemaVersion,
            source = source,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            intent = intent,
            actionType = actionType,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            params = params,
            confidence = confidence,
            language = language,
            assistantReply = assistantReply,
            reason = reason,
            errors = errors
        )
    }

    private fun Map<String, Any?>.jsonString(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.jsonString(key: String, default: String): String = (this[key] as? String) ?: default
    private fun Map<String, Any?>.jsonBoolean(key: String, default: Boolean): Boolean = (this[key] as? Boolean) ?: default
    private fun Map<String, Any?>.jsonInt(key: String, default: Int): Int = (this[key] as? Int) ?: (this[key] as? Double)?.toInt() ?: default
    private fun Map<String, Any?>.jsonDouble(key: String, default: Double): Double = (this[key] as? Double) ?: (this[key] as? Int)?.toDouble() ?: default

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
                '[' -> parseArray()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-' -> parseNumber()
                else -> null
            }
        }

        private fun parseArray(): List<Any?>? {
            if (!consume('[')) return null
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index += 1
                return result
            }

            while (index < input.length) {
                val value = parseValue()
                result.add(value)
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index += 1
                        skipWhitespace()
                        continue
                    }
                    ']' -> {
                        index += 1
                        return result
                    }
                    else -> return null
                }
            }
            return null
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

        private fun parseNumber(): Number? {
            val start = index
            if (peek() == '-') index++
            while (peek()?.isDigit() == true) index++
            if (peek() == '.') {
                index++
                while (peek()?.isDigit() == true) index++
                val s = input.substring(start, index)
                return s.toDoubleOrNull()
            }
            val s = input.substring(start, index)
            return s.toIntOrNull()
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
