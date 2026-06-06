package com.nova.luna.content

import org.junit.Assert.*
import org.junit.Test

class ContentCreationIntentParserTest {
    private val parser = ContentCreationIntentParser()

    @Test
    fun testParsePptCreation() {
        val request = parser.parse("Luna, create a professional 10 slide PPT about startup growth for investors in English")
        assertEquals(ContentCreationCommandType.CREATE_PPT, request.commandType)
        assertEquals(ContentOutputType.PPT, request.outputType)
        assertTrue("Topic missing", request.requirements.topic != null)
        assertTrue("Topic wrong: ${request.requirements.topic}", request.requirements.topic?.contains("startup growth", ignoreCase = true) == true)
        assertEquals(ContentPurpose.STARTUP, request.requirements.purpose)
        assertEquals("investors", request.requirements.audience)
        assertEquals(ContentStyle.PROFESSIONAL, request.requirements.style)
        assertTrue("Length wrong: ${request.requirements.length}", request.requirements.length?.contains("10") == true)
        assertEquals("English", request.requirements.language)
    }

    @Test
    fun testParseImageCreation() {
        val request = parser.parse("Nova create an image of a futuristic assistant")
        assertEquals(ContentCreationCommandType.CREATE_IMAGE, request.commandType)
        assertEquals(ContentOutputType.IMAGE, request.outputType)
        assertTrue("Topic wrong: ${request.requirements.topic}", request.requirements.topic?.contains("futuristic assistant", ignoreCase = true) == true)
    }

    @Test
    fun testParseVideoCreation() {
        val request = parser.parse("Luna make a short video script about fitness")
        assertEquals(ContentCreationCommandType.CREATE_VIDEO, request.commandType)
        assertEquals(ContentOutputType.VIDEO, request.outputType)
        assertTrue("Topic wrong: ${request.requirements.topic}", request.requirements.topic?.contains("fitness", ignoreCase = true) == true)
    }

    @Test
    fun testParseDocumentCreation() {
        val request = parser.parse("Nova create a business proposal document")
        assertEquals(ContentCreationCommandType.CREATE_DOCUMENT, request.commandType)
        assertEquals(ContentOutputType.DOCUMENT, request.outputType)
        assertEquals(ContentPurpose.BUSINESS, request.requirements.purpose)
    }

    @Test
    fun testParseExcelCreation() {
        val request = parser.parse("Luna create an Excel budget tracker")
        assertEquals(ContentCreationCommandType.CREATE_EXCEL, request.commandType)
        assertEquals(ContentOutputType.EXCEL, request.outputType)
        assertTrue("Topic wrong: ${request.requirements.topic}", request.requirements.topic?.contains("budget tracker", ignoreCase = true) == true)
    }

    @Test
    fun testParsePdfCreation() {
        val request = parser.parse("Nova make a PDF report about AI assistants")
        assertEquals(ContentCreationCommandType.CREATE_PDF, request.commandType)
        assertEquals(ContentOutputType.PDF, request.outputType)
        assertEquals(ContentPurpose.REPORT, request.requirements.purpose)
        assertTrue("Topic wrong: ${request.requirements.topic}", request.requirements.topic?.contains("AI assistants", ignoreCase = true) == true)
    }

    @Test
    fun testParseFinalize() {
        val request = parser.parse("Approve this")
        assertEquals(ContentCreationCommandType.FINALIZE_OUTPUT, request.commandType)
    }

    @Test
    fun testParseExport() {
        val request = parser.parse("Export as PDF")
        assertEquals(ContentCreationCommandType.EXPORT_FILE, request.commandType)
        assertEquals("PDF", request.requirements.exportFormat)
    }

    @Test
    fun testParseShare() {
        val request = parser.parse("Share on WhatsApp")
        assertEquals(ContentCreationCommandType.SHARE_FILE, request.commandType)
        assertEquals("WhatsApp", request.requirements.shareTarget)
    }

    @Test
    fun testParseCancel() {
        val request = parser.parse("Stop this creation")
        assertEquals(ContentCreationCommandType.CANCEL, request.commandType)
    }
}
