package org.opentaint.jvm.sast.util

object KotlinDebugInfoParser {
    fun parseOrNull(mappingInfo: String?): DebugInfo? =
        if (!mappingInfo.isNullOrEmpty())
            parseStratum(mappingInfo, STRATUM_KOTLIN, parseStratum(mappingInfo, STRATUM_KOTLIN_DEBUG, null))
        else
            null

    private class Tokenizer(private val text: String, private val headerString: String) : Iterator<String> {
        private var pos = 0
        private var currentLine: String? = null

        init {
            advance()
            while (currentLine != null && currentLine != headerString) {
                advance()
            }
            if (currentLine == headerString) {
                advance()
            }
        }

        private fun advance() {
            if (pos >= text.length) {
                currentLine = null
                return
            }
            val fromPos = pos
            while (pos < text.length && text[pos] != '\n' && text[pos] != '\r') pos++
            currentLine = text.substring(fromPos, pos)
            pos++
        }

        override fun hasNext(): Boolean {
            return currentLine != null
        }

        override fun next(): String {
            val res = currentLine ?: throw NoSuchElementException()
            advance()
            return res
        }
    }

    private fun parseStratum(mappingInfo: String, stratum: String, callSites: DebugInfo?): DebugInfo? {
        val fileMappings = linkedMapOf<Int, FileMapping>()
        val iterator = Tokenizer(mappingInfo, "${DebugInfo.STRATUM_START} $stratum")

        if (!iterator.hasNext() || iterator.next() != DebugInfo.STRATUM_FILE_PART) return null

        for (line in iterator) {
            when {
                line == DebugInfo.STRATUM_LINE_PART -> break
                line == DebugInfo.STRATUM_FILE_PART || line == DebugInfo.STRATUM_END || line.startsWith(DebugInfo.STRATUM_START) -> return null
            }

            val indexAndFileInternalName = if (line.startsWith("+ ")) line.substring(2) else line
            val fileIndex = indexAndFileInternalName.substringBefore(' ').toInt()
            val fileName = indexAndFileInternalName.substringAfter(' ')
            val path = if (line.startsWith("+ ")) iterator.next() else fileName
            fileMappings[fileIndex] = FileMapping(fileName, path)
        }

        for (line in iterator) {
            when {
                line == DebugInfo.STRATUM_LINE_PART || line == DebugInfo.STRATUM_FILE_PART -> return null
                line == DebugInfo.STRATUM_END || line.startsWith(DebugInfo.STRATUM_START) -> break
            }

            val fileSeparator = line.indexOf('#')
            if (fileSeparator < 0) return null
            val destSeparator = line.indexOf(':', fileSeparator)
            if (destSeparator < 0) return null
            val sourceRangeSeparator = line.indexOf(',').let { if (it !in fileSeparator..destSeparator) destSeparator else it }
            val destMultiplierSeparator = line.indexOf(',', destSeparator).let { if (it < 0) line.length else it }

            val file = fileMappings[line.substring(fileSeparator + 1, sourceRangeSeparator).toInt()] ?: return null
            val source = line.substring(0, fileSeparator).toInt()
            val dest = line.substring(destSeparator + 1, destMultiplierSeparator).toInt()
            val range = when {
                destMultiplierSeparator != line.length -> line.substring(destMultiplierSeparator + 1).toInt()
                sourceRangeSeparator != destSeparator -> line.substring(sourceRangeSeparator + 1, destSeparator).toInt()
                else -> 1
            }
            file.mapNewInterval(source, dest, range, callSites?.findRange(dest)?.let { it.mapDestToSource(it.dest) })
        }

        return DebugInfo(fileMappings.values.toList())
    }
}
