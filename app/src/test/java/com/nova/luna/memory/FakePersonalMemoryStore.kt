package com.nova.luna.memory

class FakePersonalMemoryStore : PersonalMemoryStore {
    private val items = mutableListOf<PersonalMemoryItem>()

    override fun save(item: PersonalMemoryItem): MemoryOperationResult {
        items.add(item)
        return MemoryOperationResult(MemoryPermissionStatus.ALLOWED, MemoryAction.SAVE, item)
    }

    override fun update(item: PersonalMemoryItem): MemoryOperationResult {
        val index = items.indexOfFirst { it.id == item.id }
        if (index != -1) {
            items[index] = item
            return MemoryOperationResult(MemoryPermissionStatus.ALLOWED, MemoryAction.UPDATE, item)
        }
        return MemoryOperationResult(MemoryPermissionStatus.NOT_FOUND, MemoryAction.UPDATE)
    }

    override fun delete(id: String): MemoryOperationResult {
        val removed = items.removeIf { it.id == id }
        return if (removed) {
            MemoryOperationResult(MemoryPermissionStatus.ALLOWED, MemoryAction.DELETE)
        } else {
            MemoryOperationResult(MemoryPermissionStatus.NOT_FOUND, MemoryAction.DELETE)
        }
    }

    override fun deleteByTypeAndKey(type: MemoryType, key: String): MemoryOperationResult {
        val removed = items.removeIf { it.type == type && it.key == key }
        return if (removed) {
            MemoryOperationResult(MemoryPermissionStatus.ALLOWED, MemoryAction.DELETE)
        } else {
            MemoryOperationResult(MemoryPermissionStatus.NOT_FOUND, MemoryAction.DELETE)
        }
    }

    override fun get(id: String): PersonalMemoryItem? = items.find { it.id == id }

    override fun getByTypeAndKey(type: MemoryType, key: String): PersonalMemoryItem? = items.find { it.type == type && it.key == key }

    override fun list(type: MemoryType?, domainScope: String?): List<PersonalMemoryItem> {
        return items.filter { item ->
            (type == null || item.type == type) &&
            (domainScope == null || item.domainScope == domainScope) &&
            item.isEnabled
        }
    }

    override fun clearAll(): MemoryOperationResult {
        items.clear()
        return MemoryOperationResult(MemoryPermissionStatus.ALLOWED, MemoryAction.CLEAR_ALL)
    }

    override fun markUsed(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = items[index]
            items[index] = item.copy(usageCount = item.usageCount + 1, lastUsedAt = System.currentTimeMillis())
        }
    }

    override fun exportSafeSummary(): String {
        return items.filter { it.sensitivity != MemorySensitivity.SENSITIVE_BLOCKED }
            .joinToString("; ") { "${it.key}=${it.value}" }
    }
}
