package com.nova.luna.memory

import android.content.Context
import android.content.SharedPreferences

class LocalPersonalMemoryStore(context: Context) : PersonalMemoryStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("luna_personal_memory", Context.MODE_PRIVATE)
    private val codec = PersonalMemoryJsonCodec()

    override fun save(item: PersonalMemoryItem): MemoryOperationResult {
        val items = getAllItems().toMutableList()
        items.add(item)
        saveAllItems(items)
        return MemoryOperationResult(
            status = MemoryPermissionStatus.ALLOWED,
            action = MemoryAction.SAVE,
            memoryItem = item,
            userMessage = "Saved."
        )
    }

    override fun update(item: PersonalMemoryItem): MemoryOperationResult {
        val items = getAllItems().toMutableList()
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item
            saveAllItems(items)
            return MemoryOperationResult(
                status = MemoryPermissionStatus.ALLOWED,
                action = MemoryAction.UPDATE,
                memoryItem = item,
                userMessage = "Updated."
            )
        }
        return MemoryOperationResult(
            status = MemoryPermissionStatus.NOT_FOUND,
            action = MemoryAction.UPDATE,
            technicalReason = "Item not found"
        )
    }

    override fun delete(id: String): MemoryOperationResult {
        val items = getAllItems().toMutableList()
        val removed = items.removeIf { it.id == id }
        if (removed) {
            saveAllItems(items)
            return MemoryOperationResult(
                status = MemoryPermissionStatus.ALLOWED,
                action = MemoryAction.DELETE,
                userMessage = "Deleted."
            )
        }
        return MemoryOperationResult(
            status = MemoryPermissionStatus.NOT_FOUND,
            action = MemoryAction.DELETE
        )
    }

    override fun deleteByTypeAndKey(type: MemoryType, key: String): MemoryOperationResult {
        val items = getAllItems().toMutableList()
        val removed = items.removeIf { it.type == type && it.key == key }
        if (removed) {
            saveAllItems(items)
            return MemoryOperationResult(
                status = MemoryPermissionStatus.ALLOWED,
                action = MemoryAction.DELETE,
                userMessage = "Deleted."
            )
        }
        return MemoryOperationResult(
            status = MemoryPermissionStatus.NOT_FOUND,
            action = MemoryAction.DELETE
        )
    }

    override fun get(id: String): PersonalMemoryItem? {
        return getAllItems().find { it.id == id }
    }

    override fun getByTypeAndKey(type: MemoryType, key: String): PersonalMemoryItem? {
        return getAllItems().find { it.type == type && it.key == key }
    }

    override fun list(type: MemoryType?, domainScope: String?): List<PersonalMemoryItem> {
        return getAllItems().filter { item ->
            (type == null || item.type == type) &&
            (domainScope == null || item.domainScope == domainScope) &&
            item.isEnabled
        }
    }

    override fun clearAll(): MemoryOperationResult {
        prefs.edit().clear().apply()
        return MemoryOperationResult(
            status = MemoryPermissionStatus.ALLOWED,
            action = MemoryAction.CLEAR_ALL,
            userMessage = "All memory cleared."
        )
    }

    override fun markUsed(id: String) {
        val items = getAllItems().toMutableList()
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = items[index]
            items[index] = item.copy(
                lastUsedAt = System.currentTimeMillis(),
                usageCount = item.usageCount + 1
            )
            saveAllItems(items)
        }
    }

    override fun exportSafeSummary(): String {
        val items = list().filter { it.sensitivity != MemorySensitivity.SENSITIVE_BLOCKED }
        if (items.isEmpty()) return "No preferences saved."
        
        return items.joinToString("; ") { item ->
            val keyLabel = item.key.replace("_", " ")
            "${item.type}: $keyLabel = ${item.value}"
        }
    }

    private fun getAllItems(): List<PersonalMemoryItem> {
        val json = prefs.getString("items", "[]") ?: "[]"
        return codec.decodeList(json)
    }

    private fun saveAllItems(items: List<PersonalMemoryItem>) {
        val json = codec.encodeList(items)
        prefs.edit().putString("items", json).apply()
    }
}

class PersonalMemoryJsonCodec {
    fun encodeList(items: List<PersonalMemoryItem>): String {
        return items.joinToString(prefix = "[", postfix = "]", separator = ",") { encode(it) }
    }

    fun decodeList(json: String): List<PersonalMemoryItem> {
        if (json == "[]" || json.isBlank()) return emptyList()
        // Simple splitting for prototype, assuming no nested commas in values for now
        // A real parser would be better, but we reuse the logic from BrainActionJsonCodec if possible
        // For Phase 8, we'll do a basic one.
        val results = mutableListOf<PersonalMemoryItem>()
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isEmpty()) return emptyList()
        
        // Split by },{ which is safer for a flat object list
        val parts = trimmed.split("},{")
        for (i in parts.indices) {
            var part = parts[i]
            if (!part.startsWith("{")) part = "{$part"
            if (!part.endsWith("}")) part = "$part}"
            decode(part)?.let { results.add(it) }
        }
        return results
    }

    fun encode(item: PersonalMemoryItem): String {
        return buildString {
            append("{")
            append("\"id\":\"").append(escape(item.id)).append("\",")
            append("\"type\":\"").append(item.type.name).append("\",")
            append("\"key\":\"").append(escape(item.key)).append("\",")
            append("\"value\":\"").append(escape(item.value)).append("\",")
            append("\"normalizedValue\":").append(item.normalizedValue?.let { "\"${escape(it)}\"" } ?: "null").append(",")
            append("\"sensitivity\":\"").append(item.sensitivity.name).append("\",")
            append("\"sourceCommand\":").append(item.sourceCommand?.let { "\"${escape(it)}\"" } ?: "null").append(",")
            append("\"createdAt\":").append(item.createdAt).append(",")
            append("\"updatedAt\":").append(item.updatedAt).append(",")
            append("\"lastUsedAt\":").append(item.lastUsedAt ?: "null").append(",")
            append("\"usageCount\":").append(item.usageCount).append(",")
            append("\"expiresAt\":").append(item.expiresAt ?: "null").append(",")
            append("\"userConfirmed\":").append(item.userConfirmed).append(",")
            append("\"domainScope\":").append(item.domainScope?.let { "\"${escape(it)}\"" } ?: "null").append(",")
            append("\"isEnabled\":").append(item.isEnabled)
            append("}")
        }
    }

    fun decode(json: String): PersonalMemoryItem? {
        // Basic key-value extractor for prototype
        try {
            val id = extract(json, "id") ?: return null
            val type = MemoryType.valueOf(extract(json, "type") ?: "UNKNOWN")
            val key = extract(json, "key") ?: ""
            val value = extract(json, "value") ?: ""
            val sensitivity = MemorySensitivity.valueOf(extract(json, "sensitivity") ?: "LOW")
            val createdAt = extractLong(json, "createdAt") ?: System.currentTimeMillis()
            val updatedAt = extractLong(json, "updatedAt") ?: System.currentTimeMillis()
            val usageCount = extractInt(json, "usageCount") ?: 0
            val userConfirmed = extractBoolean(json, "userConfirmed") ?: false
            val isEnabled = extractBoolean(json, "isEnabled") ?: true
            
            return PersonalMemoryItem(
                id = id,
                type = type,
                key = key,
                value = value,
                normalizedValue = extract(json, "normalizedValue"),
                sensitivity = sensitivity,
                sourceCommand = extract(json, "sourceCommand"),
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = extractLong(json, "lastUsedAt"),
                usageCount = usageCount,
                expiresAt = extractLong(json, "expiresAt"),
                userConfirmed = userConfirmed,
                domainScope = extract(json, "domainScope"),
                isEnabled = isEnabled
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun extract(json: String, key: String): String? {
        val pattern = "\"$key\":".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        if (json[start] == '"') {
            val end = json.indexOf('"', start + 1)
            if (end == -1) return null
            return unescape(json.substring(start + 1, end))
        } else if (json.startsWith("null", start)) {
            return null
        }
        return null
    }

    private fun extractLong(json: String, key: String): Long? {
        val pattern = "\"$key\":".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        val end = findValueEnd(json, start)
        val value = json.substring(start, end).trim()
        if (value == "null") return null
        return value.toLongOrNull()
    }

    private fun extractInt(json: String, key: String): Int? {
        val pattern = "\"$key\":".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        val end = findValueEnd(json, start)
        val value = json.substring(start, end).trim()
        if (value == "null") return null
        return value.toIntOrNull()
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val pattern = "\"$key\":".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        val end = findValueEnd(json, start)
        val value = json.substring(start, end).trim()
        if (value == "null") return null
        return value.toBooleanStrictOrNull()
    }

    private fun findValueEnd(json: String, start: Int): Int {
        var i = start
        while (i < json.length && json[i] != ',' && json[i] != '}') {
            i++
        }
        return i
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun unescape(value: String): String {
        return value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
    }
}
