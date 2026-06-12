package com.nova.luna.confirmation

import com.nova.luna.model.BrainAction
import com.nova.luna.model.BrainActionType
import com.nova.luna.model.BrainRiskLevel
import com.nova.luna.model.SafetyDecision
import com.nova.luna.model.SafetyStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class ConfirmationManagerTest {

    private val manager = ConfirmationManager()

    @Test
    fun testCreateAndConfirm() {
        val id = UUID.randomUUID().toString()
        val action = BrainAction(
            intent = "test",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = true
        )
        val request = ConfirmationRequest(id, action, SafetyDecision.requireConfirmation("test"), "Title", "Summary", emptyMap())
        
        manager.createConfirmation(request)
        
        val pending = manager.getPendingConfirmation(id)
        assertNotNull(pending)
        
        val result = manager.confirm(id)
        assertEquals(ConfirmationStatus.CONFIRMED, result.status)
        
        assertNull(manager.getPendingConfirmation(id))
    }
    
    @Test
    fun testCancel() {
        val id = UUID.randomUUID().toString()
        val action = BrainAction(
            intent = "test",
            actionType = BrainActionType.EXTERNAL_ACTION,
            riskLevel = BrainRiskLevel.LOW,
            requiresConfirmation = true
        )
        val request = ConfirmationRequest(id, action, SafetyDecision.requireConfirmation("test"), "Title", "Summary", emptyMap())
        
        manager.createConfirmation(request)
        
        val result = manager.cancel(id)
        assertEquals(ConfirmationStatus.CANCELLED, result.status)
        
        assertNull(manager.getPendingConfirmation(id))
    }
}
