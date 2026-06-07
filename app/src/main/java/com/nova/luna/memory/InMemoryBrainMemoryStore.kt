package com.nova.luna.memory

class InMemoryBrainMemoryStore(
    initialSnapshot: BrainMemorySnapshot = BrainMemorySnapshot()
) : BrainMemoryStore {
    private var snapshot: BrainMemorySnapshot = initialSnapshot

    override fun snapshot(): BrainMemorySnapshot = synchronized(this) {
        snapshot
    }

    override fun replace(snapshot: BrainMemorySnapshot) {
        synchronized(this) {
            this.snapshot = snapshot.copy(
                preferences = snapshot.preferences,
                updatedAtMillis = snapshot.updatedAtMillis
            )
        }
    }

    override fun update(transform: (BrainMemorySnapshot) -> BrainMemorySnapshot): BrainMemorySnapshot {
        return synchronized(this) {
            val updated = transform(snapshot)
            snapshot = updated.copy(
                preferences = updated.preferences,
                updatedAtMillis = updated.updatedAtMillis
            )
            snapshot
        }
    }

    override fun getPreferences(): LocalUserPreferences = snapshot().preferences

    override fun setPreferences(preferences: LocalUserPreferences) {
        update { current ->
            current.copy(
                preferences = preferences,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    override fun updatePreferences(transform: (LocalUserPreferences) -> LocalUserPreferences): LocalUserPreferences {
        return update { current ->
            current.copy(
                preferences = transform(current.preferences),
                updatedAtMillis = System.currentTimeMillis()
            )
        }.preferences
    }
}
