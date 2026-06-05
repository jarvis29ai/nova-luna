package com.nova.luna.phone

import java.util.*
import java.util.regex.Pattern

class PhoneContactIntentParser {

    private val phonePattern = Pattern.compile("(\\+91|0)?[6-9]\\d{9}|\\d{10}|\\d{3}[-\\s]\\d{3}[-\\s]\\d{4}")

    fun parse(rawText: String): PhoneContactRequest {
        val normalized = rawText.lowercase(Locale.US).trim()
        
        return when {
            isCancel(normalized) -> PhoneContactRequest(PhoneContactCommandType.CANCEL, rawText = rawText)
            isCreateContact(normalized) -> parseCreateContact(normalized, rawText)
            isUpdateContact(normalized) -> parseUpdateContact(normalized, rawText)
            isCallFromMessage(normalized) -> parseCallFromMessage(normalized, rawText)
            isCallCommand(normalized) -> parseCallCommand(normalized, rawText)
            else -> PhoneContactRequest(PhoneContactCommandType.UNKNOWN, rawText = rawText)
        }
    }

    private fun isCancel(text: String): Boolean {
        return text.contains("cancel") || text == "stop" || text == "no" || text == "don't"
    }

    private fun isCallCommand(text: String): Boolean {
        return text.startsWith("call") || text.contains("dial") || text.contains("find") && text.contains("call")
    }

    private fun isCreateContact(text: String): Boolean {
        return text.contains("save") || text.contains("create") || text.contains("add") && text.contains("contact")
    }

    private fun isUpdateContact(text: String): Boolean {
        return text.contains("update") && text.contains("contact") || text.contains("update") && text.contains("number")
    }

    private fun isCallFromMessage(text: String): Boolean {
        return (text.contains("number from") || text.contains("number sent by")) && 
               (text.contains("message") || text.contains("whatsapp") || text.contains("telegram") || text.contains("sms"))
    }

    private fun parseCallCommand(text: String, raw: String): PhoneContactRequest {
        val number = extractNumber(text)
        if (number != null && (text.contains("unknown") || text.startsWith("call " + number))) {
            return PhoneContactRequest(
                commandType = PhoneContactCommandType.CALL_UNKNOWN_PERSON,
                phoneNumber = number,
                rawText = raw
            )
        }

        val name = extractNameAfter(text, listOf("call", "dial", "find"))
        return if (name.isNullOrBlank() && number != null) {
            PhoneContactRequest(
                commandType = PhoneContactCommandType.CALL_UNKNOWN_PERSON,
                phoneNumber = number,
                rawText = raw
            )
        } else {
            PhoneContactRequest(
                commandType = PhoneContactCommandType.CALL_SAVED_CONTACT,
                contactName = name,
                label = extractLabel(text),
                rawText = raw
            )
        }
    }

    private fun parseCallFromMessage(text: String, raw: String): PhoneContactRequest {
        val source = when {
            text.contains("whatsapp") -> "WhatsApp"
            text.contains("telegram") -> "Telegram"
            text.contains("sms") -> "SMS"
            else -> "Message"
        }
        
        val sender = extractNameAfter(text, listOf("from", "sent by"))
        
        return PhoneContactRequest(
            commandType = PhoneContactCommandType.CALL_NUMBER_FROM_MESSAGE,
            senderName = sender,
            sourceApp = source,
            rawText = raw
        )
    }

    private fun parseCreateContact(text: String, raw: String): PhoneContactRequest {
        val number = extractNumber(text)
        val name = extractNameAfter(text, listOf("as", "for", "contact"))
        
        return PhoneContactRequest(
            commandType = PhoneContactCommandType.CREATE_NEW_CONTACT,
            contactName = name,
            phoneNumber = number,
            rawText = raw
        )
    }

    private fun parseUpdateContact(text: String, raw: String): PhoneContactRequest {
        val name = extractNameAfter(text, listOf("update"))
        val number = extractNumber(text)
        
        return PhoneContactRequest(
            commandType = PhoneContactCommandType.UPDATE_CONTACT,
            contactName = name,
            phoneNumber = number,
            rawText = raw
        )
    }

    private fun extractNumber(text: String): String? {
        val matcher = phonePattern.matcher(text)
        return if (matcher.find()) {
            matcher.group().filter { it.isDigit() || it == '+' }
        } else null
    }

    private fun extractNameAfter(text: String, keywords: List<String>): String? {
        for (keyword in keywords) {
            val index = text.indexOf(keyword)
            if (index != -1) {
                var namePart = text.substring(index + keyword.length).trim()
                // Remove common prefix "for " if keyword was "contact"
                if (keyword == "contact" && namePart.startsWith("for ")) {
                    namePart = namePart.removePrefix("for ").trim()
                }
                // Remove common trailing words FIRST
                namePart = namePart.split(" number", " mobile", " work", " home", " office", " from", " on ", " message", " whatsapp", " telegram").first()
                // Stop at first digit
                val digitIndex = namePart.indexOfFirst { it.isDigit() }
                if (digitIndex != -1) {
                    namePart = namePart.substring(0, digitIndex).trim()
                }
                // Remove possessive 's
                namePart = namePart.removeSuffix("'s")
                if (namePart.isNotEmpty()) return namePart
            }
        }
        return null
    }

    private fun extractLabel(text: String): String? {
        return when {
            text.contains("mobile") -> "mobile"
            text.contains("work") -> "work"
            text.contains("home") -> "home"
            text.contains("office") -> "office"
            else -> null
        }
    }

    fun parseSelection(text: String): Int? {
        val normalized = text.lowercase()
        return when {
            normalized.contains("first") || normalized.contains("1st") || normalized == "1" -> 0
            normalized.contains("second") || normalized.contains("2nd") || normalized == "2" -> 1
            normalized.contains("third") || normalized.contains("3rd") || normalized == "3" -> 2
            else -> null
        }
    }

    fun isConfirmation(text: String): Boolean? {
        val normalized = text.lowercase().trim()
        return when {
            normalized == "yes" || normalized == "yeah" || normalized == "sure" || normalized == "ok" || normalized == "yep" || 
            normalized.startsWith("yes ") || normalized.startsWith("yeah ") || normalized.startsWith("sure ") -> true
            normalized == "no" || normalized == "cancel" || normalized == "stop" || normalized == "nope" ||
            normalized.startsWith("no ") || normalized.startsWith("cancel ") || normalized.startsWith("stop ") -> false
            else -> null
        }
    }
}
