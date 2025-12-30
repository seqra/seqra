package org.opentaint.jvm.sast.util

import kotlin.math.max

const val STRATUM_KOTLIN = "Kotlin"
const val STRATUM_KOTLIN_DEBUG = "KotlinDebug"

class DebugInfo(val fileMappings: List<FileMapping>) {
    private val intervals = fileMappings.flatMap { it.lineMappings }.sortedBy { it.dest }

    fun findRange(lineNumber: Int): RangeMapping? {
        val index = intervals.binarySearch { if (lineNumber in it) 0 else it.dest - lineNumber }
        return if (index < 0) null else intervals[index]
    }

    companion object {
        const val STRATUM_FILE_PART = "*F"
        const val STRATUM_LINE_PART = "*L"
        const val STRATUM_START = "*S"
        const val STRATUM_END = "*E"
    }
}

class FileMapping(val name: String, val path: String) {
    val lineMappings = arrayListOf<RangeMapping>()

    fun mapNewLineNumber(source: Int, currentIndex: Int, callSite: SourcePosition?): Int {
        val mapping = lineMappings.lastOrNull()?.takeIf { it.canReuseFor(source, currentIndex, callSite) }
            ?: lineMappings.firstOrNull()?.takeIf { it.canReuseFor(source, currentIndex, callSite) }
            ?: mapNewInterval(source, currentIndex + 1, 1, callSite)
        mapping.range = max(mapping.range, source - mapping.source + 1)
        return mapping.mapSourceToDest(source)
    }

    private fun RangeMapping.canReuseFor(newSource: Int, globalMaxDest: Int, newCallSite: SourcePosition?): Boolean =
        callSite == newCallSite && (newSource - source) in 0 until range + (if (globalMaxDest in this) 10 else 0)

    fun mapNewInterval(source: Int, dest: Int, range: Int, callSite: SourcePosition? = null): RangeMapping =
        RangeMapping(source, dest, range, callSite, parent = this).also { lineMappings.add(it) }
}

data class RangeMapping(val source: Int, val dest: Int, var range: Int, val callSite: SourcePosition?, val parent: FileMapping) {
    operator fun contains(destLine: Int): Boolean =
        dest <= destLine && destLine < dest + range

    fun hasMappingForSource(sourceLine: Int): Boolean =
        source <= sourceLine && sourceLine < source + range

    fun mapDestToSource(destLine: Int): SourcePosition =
        SourcePosition(source + (destLine - dest), parent.name, parent.path)

    fun mapSourceToDest(sourceLine: Int): Int =
        dest + (sourceLine - source)
}

val RangeMapping.toRange: IntRange
    get() = dest until dest + range

data class SourcePosition(val line: Int, val file: String, val path: String)
