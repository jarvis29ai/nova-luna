package com.nova.luna.memory

interface BrainMemoryStore : UserPreferenceStore {
    fun snapshot(): BrainMemorySnapshot

    fun replace(snapshot: BrainMemorySnapshot)

    fun update(transform: (BrainMemorySnapshot) -> BrainMemorySnapshot): BrainMemorySnapshot
}
