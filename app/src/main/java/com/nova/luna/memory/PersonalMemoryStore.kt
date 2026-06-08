package com.nova.luna.memory

interface PersonalMemoryStore {
    fun save(item: PersonalMemoryItem): MemoryOperationResult
    fun update(item: PersonalMemoryItem): MemoryOperationResult
    fun delete(id: String): MemoryOperationResult
    fun deleteByTypeAndKey(type: MemoryType, key: String): MemoryOperationResult
    fun get(id: String): PersonalMemoryItem?
    fun getByTypeAndKey(type: MemoryType, key: String): PersonalMemoryItem?
    fun list(type: MemoryType? = null, domainScope: String? = null): List<PersonalMemoryItem>
    fun clearAll(): MemoryOperationResult
    fun markUsed(id: String)
    fun exportSafeSummary(): String
}
