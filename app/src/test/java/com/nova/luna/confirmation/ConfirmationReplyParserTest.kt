package com.nova.luna.confirmation

import org.junit.Assert.*
import org.junit.Test

class ConfirmationReplyParserTest {

    private val parser = ConfirmationReplyParser()

    @Test
    fun testParseConfirm() {
        assertEquals(ConfirmationAction.CONFIRM, parser.parseReply("yes"))
        assertEquals(ConfirmationAction.CONFIRM, parser.parseReply("confirm"))
        assertEquals(ConfirmationAction.CONFIRM, parser.parseReply("haan"))
        assertEquals(ConfirmationAction.CONFIRM, parser.parseReply("proceed"))
    }
    
    @Test
    fun testParseCancel() {
        assertEquals(ConfirmationAction.CANCEL, parser.parseReply("no"))
        assertEquals(ConfirmationAction.CANCEL, parser.parseReply("cancel"))
        assertEquals(ConfirmationAction.CANCEL, parser.parseReply("mat karo"))
    }
    
    @Test
    fun testParseUnknown() {
        assertEquals(ConfirmationAction.UNKNOWN, parser.parseReply("maybe"))
    }
}
