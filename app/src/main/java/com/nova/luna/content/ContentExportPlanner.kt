package com.nova.luna.content

class ContentExportPlanner {

    fun planExport(outputType: ContentOutputType, format: String?): String {
        val finalFormat = format ?: when (outputType) {
            ContentOutputType.PPT -> "PPTX"
            ContentOutputType.IMAGE -> "PNG"
            ContentOutputType.VIDEO -> "MP4"
            ContentOutputType.DOCUMENT -> "DOCX"
            ContentOutputType.EXCEL -> "XLSX"
            ContentOutputType.PDF -> "PDF"
            else -> "TXT"
        }
        return "Exporting as $finalFormat"
    }
}
