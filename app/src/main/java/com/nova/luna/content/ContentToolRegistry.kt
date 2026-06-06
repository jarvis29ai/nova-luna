package com.nova.luna.content

import android.content.Context
import android.content.pm.PackageManager

class ContentToolRegistry(private val context: Context) {

    fun getAvailableTools(): List<CreationTool> {
        val pm = context.packageManager
        val tools = mutableListOf<CreationTool>()

        // ChatGPT
        tools.add(createTool("chatgpt", "ChatGPT", "com.openai.chatgpt", listOf(ContentOutputType.PPT, ContentOutputType.DOCUMENT, ContentOutputType.IMAGE), pm))
        
        // Claude
        tools.add(createTool("claude", "Claude", "com.anthropic.claude", listOf(ContentOutputType.PPT, ContentOutputType.DOCUMENT), pm))
        
        // Gemini
        tools.add(createTool("gemini", "Gemini", "com.google.android.apps.bard", listOf(ContentOutputType.PPT, ContentOutputType.DOCUMENT, ContentOutputType.IMAGE), pm))
        
        // Canva
        tools.add(createTool("canva", "Canva", "com.canva.editor", listOf(ContentOutputType.PPT, ContentOutputType.IMAGE, ContentOutputType.VIDEO, ContentOutputType.PDF), pm))
        
        // CapCut
        tools.add(createTool("capcut", "CapCut", "com.lemon.lvoverseas", listOf(ContentOutputType.VIDEO), pm))
        
        // Google Docs
        tools.add(createTool("google_docs", "Google Docs", "com.google.android.apps.docs.editors.docs", listOf(ContentOutputType.DOCUMENT, ContentOutputType.PDF), pm))
        
        // Google Sheets
        tools.add(createTool("google_sheets", "Google Sheets", "com.google.android.apps.docs.editors.sheets", listOf(ContentOutputType.EXCEL), pm))
        
        // MS PowerPoint
        tools.add(createTool("ms_powerpoint", "PowerPoint", "com.microsoft.office.powerpoint", listOf(ContentOutputType.PPT), pm))
        
        // MS Word
        tools.add(createTool("ms_word", "Word", "com.microsoft.office.word", listOf(ContentOutputType.DOCUMENT), pm))
        
        // MS Excel
        tools.add(createTool("ms_excel", "Excel", "com.microsoft.office.excel", listOf(ContentOutputType.EXCEL), pm))

        // Local Internal Builder
        tools.add(CreationTool(
            id = "internal",
            displayName = "Luna/Nova Internal",
            supportedTypes = ContentOutputType.entries.toList(),
            isInstalled = true,
            isPro = false,
            launchSupport = false
        ))

        return tools
    }

    private fun createTool(id: String, name: String, pkg: String, types: List<ContentOutputType>, pm: PackageManager): CreationTool {
        val installed = isAppInstalled(pkg, pm)
        return CreationTool(
            id = id,
            displayName = name,
            packageName = pkg,
            supportedTypes = types,
            isInstalled = installed,
            isPro = false // We don't know unless we have a way to check
        )
    }

    private fun isAppInstalled(packageName: String, pm: PackageManager): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
