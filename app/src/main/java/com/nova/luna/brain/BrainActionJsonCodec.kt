package com.nova.luna.brain

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionSource
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel

class BrainActionJsonCodec {
    fun encode(action: BrainAction): String {
        val paramsJson = action.params.entries.joinToString(",") { (k, v) ->
            "\"${escape(k)}\":\"${escape(v)}\""
        }
        val errorsJson = action.errors.joinToString(",") { "\"${escape(it)}\"" }

        return buildString {
            append('{')
            append("\"schemaVersion\":").append(action.schemaVersion).append(",")
            append("\"source\":\"").append(action.source.name).append("\",")
            append("\"rawCommand\":\"").append(escape(action.rawCommand)).append("\",")
            append("\"normalizedCommand\":\"").append(escape(action.normalizedCommand)).append("\",")
            append("\"intent\":\"").append(escape(action.intent)).append("\",")
            append("\"reply\":\"").append(escape(action.reply)).append("\",")
            append("\"actionType\":\"").append(action.actionType.name).append("\",")
            append("\"riskLevel\":\"").append(action.riskLevel.name).append("\",")
            append("\"requiresConfirmation\":").append(action.requiresConfirmation).append(",")
            append("\"confidence\":").append(action.confidence).append(",")
            append("\"language\":\"").append(escape(action.language)).append("\",")
            append("\"params\":{").append(paramsJson).append("},")
            append("\"assistantReply\":\"").append(escape(action.assistantReply)).append("\",")
            append("\"reason\":\"").append(escape(action.reason)).append("\",")
            append("\"nextQuestion\":").append(action.nextQuestion?.let { "\"${escape(it)}\"" } ?: "null").append(",")
            append("\"finalActionAllowed\":").append(action.finalActionAllowed).append(",")
            append("\"errors\":[").append(errorsJson).append("]")
            append('}')
        }
    }

    fun decode(json: String?): BrainAction? {
        if (json.isNullOrBlank()) return null
        val parser = JsonParser(json)
        val root = parser.parseObject() ?: return null

        val intent = root.jsonString("intent") ?: return null
        val actionTypeStr = root.jsonString("actionType")
        val actionType = BrainActionType.entries.find { it.name == actionTypeStr } ?: BrainActionType.UNKNOWN
        val riskLevelStr = root.jsonString("riskLevel")
        val riskLevel = BrainRiskLevel.entries.find { it.name == riskLevelStr } ?: BrainRiskLevel.UNKNOWN
        val requiresConfirmation = root.jsonBoolean("requiresConfirmation", false)
        
        val params = mutableMapOf<String, String>()
        val paramsObj = root["params"] as? Map<*, *>
        paramsObj?.forEach { (k, v) ->
            if (k is String && v is String) params[k] = v
        }

        val confidence = root.jsonDouble("confidence", 0.0)
        val reply = root.jsonString("reply") ?: ""
        val assistantReply = root.jsonString("assistantReply") ?: ""
        val reason = root.jsonString("reason") ?: ""
        val language = root.jsonString("language") ?: "unknown"
        val sourceStr = root.jsonString("source")
        val source = runCatching { BrainActionSource.valueOf(sourceStr ?: "MODEL") }.getOrDefault(BrainActionSource.MODEL)
        val rawCommand = root.jsonString("rawCommand") ?: ""
        val normalizedCommand = root.jsonString("normalizedCommand") ?: ""
        val schemaVersion = root.jsonInt("schemaVersion", 1)
        val nextQuestion = root.jsonString("nextQuestion")
        val finalActionAllowed = root.jsonBoolean("finalActionAllowed", !requiresConfirmation)
        
        val errors = mutableListOf<String>()
        val errorsList = root["errors"] as? List<*>
        errorsList?.forEach { if (it is String) errors.add(it) }

        return BrainAction(
            schemaVersion = schemaVersion,
            source = source,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            intent = intent,
            reply = reply,
            actionType = actionType,
            riskLevel = riskLevel,
            requiresConfirmation = requiresConfirmation,
            params = params,
            confidence = confidence,
            language = language,
            assistantReply = assistantReply,
            reason = reason,
            errors = errors,
            nextQuestion = nextQuestion,
            finalActionAllowed = finalActionAllowed
        )
    }

    private fun Map<String, Any?>.jsonString(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.jsonBoolean(key: String, default: Boolean): Boolean = (this[key] as? Boolean) ?: default
    private fun Map<String, Any?>.jsonInt(key: String, default: Int): Int = (this[key] as? Number)?.toInt() ?: default
    private fun Map<String, Any?>.jsonDouble(key: String, default: Double): Double = (this[key] as? Number)?.toDouble() ?: default

    private fun escape(s: String): String {
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    class JsonParser(private val input: String) {
        private var index = 0

        fun parseObject(): Map<String, Any?>? {
            skipWhitespace()
            if (!consume('{')) return null

            val result = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                consume('}')
                return result
            }

            while (index < input.length) {
                skipWhitespace()
                val key = parseString() ?: return null
                skipWhitespace()
                if (!consume(':')) return null
                
                val valueResult = parseValueInternal()
                if (valueResult is Error) return null
                
                result[key] = if (valueResult is Null) null else valueResult
                
                skipWhitespace()
                val next = peek()
                if (next == ',') {
                    consume(',')
                } else if (next == '}') {
                    break
                } else {
                    return null
                }
            }
            if (!consume('}')) return null
            return result
        }

        private fun parseArray(): List<Any?>? {
            if (!consume('[')) return null
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                consume(']')
                return result
            }

            while (index < input.length) {
                val valueResult = parseValueInternal()
                if (valueResult is Error) return null
                result.add(if (valueResult is Null) null else valueResult)
                
                skipWhitespace()
                val next = peek()
                if (next == ',') {
                    consume(',')
                } else if (next == ']') {
                    break
                } else {
                    return null
                }
            }
            if (!consume(']')) return null
            return result
        }

        private fun parseValueInternal(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseObject() ?: Error
                '[' -> parseArray() ?: Error
                '"' -> parseString() ?: Error
                't', 'f' -> parseBoolean() ?: Error
                'n' -> parseNullValue() ?: Error
                else -> parseNumber() ?: Error
            }
        }

        private fun parseNullValue(): Any? {
            if (input.startsWith("null", index)) {
                index += 4
                return Null
            }
            return null
        }

        private fun parseString(): String? {
            skipWhitespace()
            if (!consume('"')) return null
            val sb = StringBuilder()
            while (index < input.length) {
                val c = input[index++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (index >= input.length) return null
                    when (val escaped = input[index++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (index + 4 > input.length) return null
                            val hex = input.substring(index, index + 4)
                            sb.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> sb.append(escaped)
                    }
                } else {
                    sb.append(c)
                }
            }
            return null
        }

        private fun parseBoolean(): Boolean? {
            if (input.startsWith("true", index)) {
                index += 4
                return true
            }
            if (input.startsWith("false", index)) {
                index += 5
                return false
            }
            return null
        }

        private fun parseNumber(): Number? {
            val start = index
            if (peek() == '-') index += 1
            while (peek()?.isDigit() == true) index += 1
            if (peek() == '.') {
                index += 1
                while (peek()?.isDigit() == true) index += 1
                val s = input.substring(start, index)
                return s.toDoubleOrNull()
            }
            val s = input.substring(start, index)
            if (s.isEmpty()) return null
            return s.toLongOrNull()
        }

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) index += 1
        }

        private fun peek(): Char? {
            return if (index < input.length) input[index] else null
        }

        private fun consume(expected: Char): Boolean {
            if (peek() != expected) return false
            index += 1
            return true
        }

        private object Null
        private object Error
    }
}
