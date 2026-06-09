package com.nova.luna.modelinstall

internal object SimpleJson {
    fun stringify(value: Any?, indentSpaces: Int = 2): String {
        return buildString {
            writeValue(this, value, indentSpaces.coerceAtLeast(0), 0)
        }
    }

    fun parseObject(text: String): Map<String, Any?> {
        val parser = JsonParser(text)
        val value = parser.parseValue()
        parser.ensureFullyConsumed()

        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?>
            ?: error("Expected JSON object at the root")
    }

    fun parseArray(text: String): List<Any?> {
        val parser = JsonParser(text)
        val value = parser.parseValue()
        parser.ensureFullyConsumed()

        @Suppress("UNCHECKED_CAST")
        return value as? List<Any?>
            ?: error("Expected JSON array at the root")
    }

    private fun writeValue(builder: StringBuilder, value: Any?, indentSpaces: Int, depth: Int) {
        when (value) {
            null -> builder.append("null")
            is String -> writeString(builder, value)
            is Boolean, is Number -> builder.append(value.toString())
            is Map<*, *> -> writeObject(builder, value, indentSpaces, depth)
            is Iterable<*> -> writeArray(builder, value, indentSpaces, depth)
            else -> error("Unsupported JSON value type: ${value::class.qualifiedName}")
        }
    }

    private fun writeObject(
        builder: StringBuilder,
        value: Map<*, *>,
        indentSpaces: Int,
        depth: Int
    ) {
        builder.append('{')
        if (value.isNotEmpty()) {
            val pretty = indentSpaces > 0
            if (pretty) builder.append('\n')
            val iterator = value.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (pretty) appendIndent(builder, indentSpaces, depth + 1)
                writeString(builder, entry.key?.toString() ?: "")
                builder.append(if (pretty) ": " else ":")
                writeValue(builder, entry.value, indentSpaces, depth + 1)
                if (iterator.hasNext()) {
                    builder.append(',')
                }
                if (pretty) builder.append('\n')
            }
            if (pretty) appendIndent(builder, indentSpaces, depth)
        }
        builder.append('}')
    }

    private fun writeArray(
        builder: StringBuilder,
        value: Iterable<*>,
        indentSpaces: Int,
        depth: Int
    ) {
        builder.append('[')
        val items = value.toList()
        if (items.isNotEmpty()) {
            val pretty = indentSpaces > 0
            if (pretty) builder.append('\n')
            items.forEachIndexed { index, item ->
                if (pretty) appendIndent(builder, indentSpaces, depth + 1)
                writeValue(builder, item, indentSpaces, depth + 1)
                if (index < items.lastIndex) {
                    builder.append(',')
                }
                if (pretty) builder.append('\n')
            }
            if (pretty) appendIndent(builder, indentSpaces, depth)
        }
        builder.append(']')
    }

    private fun writeString(builder: StringBuilder, value: String) {
        builder.append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        builder.append("\\u")
                        builder.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        builder.append('"')
    }

    private fun appendIndent(builder: StringBuilder, indentSpaces: Int, depth: Int) {
        repeat(indentSpaces * depth) {
            builder.append(' ')
        }
    }

    private class JsonParser(private val text: String) {
        private var index: Int = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (index >= text.length) {
                error("Unexpected end of JSON input")
            }

            return when (val ch = text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> {
                    if (ch == '-' || ch.isDigit()) {
                        parseNumber()
                    } else {
                        error("Unexpected character '$ch' at position $index")
                    }
                }
            }
        }

        fun ensureFullyConsumed() {
            skipWhitespace()
            if (index != text.length) {
                error("Unexpected trailing content at position $index")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            if (consumeIf('}')) {
                return linkedMapOf()
            }

            val result = linkedMapOf<String, Any?>()
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                when {
                    consumeIf('}') -> return result
                    consumeIf(',') -> continue
                    else -> error("Expected ',' or '}' at position $index")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            if (consumeIf(']')) {
                return emptyList()
            }

            val result = mutableListOf<Any?>()
            while (true) {
                result += parseValue()
                skipWhitespace()
                when {
                    consumeIf(']') -> return result
                    consumeIf(',') -> continue
                    else -> error("Expected ',' or ']' at position $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                val ch = text[index++]
                when (ch) {
                    '"' -> return result.toString()
                    '\\' -> result.append(parseEscape())
                    else -> result.append(ch)
                }
            }
            error("Unterminated string literal")
        }

        private fun parseEscape(): Char {
            if (index >= text.length) {
                error("Unterminated escape sequence")
            }

            return when (val ch = text[index++]) {
                '"', '\\', '/' -> ch
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> error("Unsupported escape sequence \\$ch at position ${index - 1}")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > text.length) {
                error("Invalid unicode escape at position $index")
            }

            val value = text.substring(index, index + 4)
            index += 4
            return value.toInt(16).toChar()
        }

        private fun parseLiteral(expected: String, value: Any?): Any? {
            if (!text.startsWith(expected, index)) {
                error("Expected '$expected' at position $index")
            }
            index += expected.length
            return value
        }

        private fun parseNumber(): Number {
            val start = index
            if (text[index] == '-') {
                index++
            }

            readDigits()

            var floatingPoint = false
            if (peekChar('.') ) {
                floatingPoint = true
                index++
                readDigits()
            }

            if (peekChar('e') || peekChar('E')) {
                floatingPoint = true
                index++
                if (peekChar('+') || peekChar('-')) {
                    index++
                }
                readDigits()
            }

            val token = text.substring(start, index)
            return if (floatingPoint) token.toDouble() else token.toLong()
        }

        private fun readDigits() {
            val start = index
            while (index < text.length && text[index].isDigit()) {
                index++
            }
            if (index == start) {
                error("Expected digit at position $index")
            }
        }

        private fun peekChar(expected: Char): Boolean {
            return index < text.length && text[index] == expected
        }

        private fun consumeIf(expected: Char): Boolean {
            return if (index < text.length && text[index] == expected) {
                index++
                true
            } else {
                false
            }
        }

        private fun expect(expected: Char) {
            if (!consumeIf(expected)) {
                error("Expected '$expected' at position $index")
            }
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
        }
    }
}

internal fun Map<String, Any?>.jsonString(key: String, default: String = ""): String {
    return when (val value = this[key]) {
        null -> default
        is String -> value
        else -> error("Expected string value for '$key'")
    }
}

internal fun Map<String, Any?>.jsonStringOrNull(key: String): String? {
    return when (val value = this[key]) {
        null -> null
        is String -> value
        else -> error("Expected string value for '$key'")
    }
}

internal fun Map<String, Any?>.jsonLongOrNull(key: String): Long? {
    return when (val value = this[key]) {
        null -> null
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
            ?: error("Expected numeric value for '$key'")
        else -> error("Expected numeric value for '$key'")
    }
}

internal fun Map<String, Any?>.jsonIntOrNull(key: String): Int? {
    return jsonLongOrNull(key)?.toInt()
}

internal fun Map<String, Any?>.jsonObject(key: String): Map<String, Any?> {
    return when (val value = this[key]) {
        null -> error("Missing object value for '$key'")
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            value as Map<String, Any?>
        }
        else -> error("Expected object value for '$key'")
    }
}

internal fun Map<String, Any?>.jsonArray(key: String): List<Any?> {
    return when (val value = this[key]) {
        null -> emptyList()
        is List<*> -> value.map { it }
        else -> error("Expected array value for '$key'")
    }
}
