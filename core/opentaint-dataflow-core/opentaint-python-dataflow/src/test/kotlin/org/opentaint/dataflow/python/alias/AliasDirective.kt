package org.opentaint.dataflow.python.alias

import org.opentaint.dataflow.ap.ifds.AccessPathBase

object AliasDirective {
    private const val ALIAS = "# alias:"
    private const val DEPTH = "# depth:"

    private val PATH = Regex("""^([!?]?)\s*arg(\d+)((?:\[]|\.\w+)*)$""")
    private val ACCESSOR = Regex("""\[]|\.(\w+)""")

    enum class Kind(val marker: String) { PRESENT(""), ABSENT("!"), GAP("?") }

    class Parsed(val depth: Int, val byLine: Map<Int, List<Pair<Kind, AliasApInfo>>>)

    fun parse(src: String): Parsed {
        val lines = src.lines().map { it.trim() }
        val depth = lines.lastOrNull { it.startsWith(DEPTH) }?.removePrefix(DEPTH)?.trim()?.toInt() ?: 0
        val byLine = lines.withIndex().filter { it.value.startsWith(ALIAS) }.associate { (i, line) ->
            i + 1 to line.removePrefix(ALIAS).split(",").map { it.trim() }.filter { it.isNotEmpty() }.map(::parsePath)
        }
        return Parsed(depth, byLine)
    }

    private fun parsePath(raw: String): Pair<Kind, AliasApInfo> {
        val (marker, index, accessors) = PATH.matchEntire(raw)?.destructured ?: error("Bad alias path: $raw")
        val path = ACCESSOR.findAll(accessors).map { m ->
            m.groups[1]?.let { AliasAccessor.Field(it.value) } ?: AliasAccessor.Array
        }.toList()
        return Kind.entries.single { it.marker == marker } to AliasApInfo(AccessPathBase.Argument(index.toInt()), path)
    }
}
