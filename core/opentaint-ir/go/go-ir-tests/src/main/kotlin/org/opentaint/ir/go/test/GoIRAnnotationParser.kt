package org.opentaint.ir.go.test

/**
 * Parses `//@ inst("GoIRBinOp", op=ADD)` style annotations from Go source files.
 *
 * Supported annotation kinds:
 * - `//@ inst("InstructionType", field=value, ...)` — assert instruction on this line
 * - `//@ count("InstructionType", n)` — assert count of instruction type in enclosing function
 * - `//@ call(target="pkg.Func", mode=DIRECT)` — assert call target
 * - `//@ type("expected type string")` — assert type of expression on this line
 * - `//@ entity(kind="Function", name="Foo")` — assert entity exists
 * - `//@ cfg(blocks=N)` — assert block count in enclosing function
 * - `//@ sanity` — only run sanity checker (no specific instruction assertions)
 *
 * Annotations are associated with the Go source line they appear on.
 * Function context is determined by the `//go:ir-test func=FuncName` directive.
 */
object GoIRAnnotationParser {

    // Matches: //go:ir-test func=FuncName
    private val FUNC_DIRECTIVE = Regex("""//go:ir-test\s+func=(\w+)""")

    // Matches: //@ kind("args", field=value, ...)
    private val ANNOTATION_PATTERN = Regex("""//@ (\w+)\((.+?)\)\s*$""")

    // Matches: //@ sanity (no args)
    private val SANITY_PATTERN = Regex("""//@ sanity\s*$""")

    fun parse(source: String): GoIRAnnotationFile {
        val lines = source.lines()
        val annotations = mutableListOf<GoIRAnnotation>()
        var currentFunction: String? = null
        var hasSanityOnly = false

        lines.forEachIndexed { lineIndex, line ->
            val lineNum = lineIndex + 1

            // Check for function directive
            FUNC_DIRECTIVE.find(line)?.let { match ->
                currentFunction = match.groupValues[1]
            }

            // Check for sanity-only annotation
            if (SANITY_PATTERN.containsMatchIn(line)) {
                hasSanityOnly = true
            }

            // Check for annotation
            ANNOTATION_PATTERN.find(line)?.let { match ->
                val kind = match.groupValues[1]
                val argsStr = match.groupValues[2]
                val parsed = parseAnnotationArgs(kind, argsStr)
                annotations.add(
                    GoIRAnnotation(
                        line = lineNum,
                        kind = kind,
                        function = currentFunction,
                        args = parsed,
                    )
                )
            }
        }

        return GoIRAnnotationFile(
            annotations = annotations,
            hasSanityOnly = hasSanityOnly || annotations.isEmpty(),
        )
    }

    private fun parseAnnotationArgs(kind: String, argsStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val trimmed = argsStr.trim()

        when (kind) {
            "inst" -> {
                // First arg is instruction type in quotes: "GoIRBinOp"
                val parts = splitRespectingQuotes(trimmed)
                if (parts.isNotEmpty()) {
                    result["instType"] = parts[0].removeSurrounding("\"")
                }
                // Remaining are key=value pairs
                for (i in 1 until parts.size) {
                    val kv = parts[i].split("=", limit = 2)
                    if (kv.size == 2) {
                        result[kv[0].trim()] = kv[1].trim().removeSurrounding("\"")
                    }
                }
            }
            "count" -> {
                // count("InstructionType", n)
                val parts = splitRespectingQuotes(trimmed)
                if (parts.isNotEmpty()) {
                    result["instType"] = parts[0].removeSurrounding("\"")
                }
                if (parts.size >= 2) {
                    result["count"] = parts[1].trim()
                }
            }
            "call" -> {
                // call(target="pkg.Func", mode=DIRECT)
                for (part in splitRespectingQuotes(trimmed)) {
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) {
                        result[kv[0].trim()] = kv[1].trim().removeSurrounding("\"")
                    }
                }
            }
            "type" -> {
                // type("expected type string")
                result["expectedType"] = trimmed.removeSurrounding("\"")
            }
            "entity" -> {
                // entity(kind="Function", name="Foo")
                for (part in splitRespectingQuotes(trimmed)) {
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) {
                        result[kv[0].trim()] = kv[1].trim().removeSurrounding("\"")
                    }
                }
            }
            "cfg" -> {
                // cfg(blocks=N) or cfg(succs=[1,2])
                for (part in splitRespectingQuotes(trimmed)) {
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) {
                        result[kv[0].trim()] = kv[1].trim()
                    }
                }
            }
        }
        return result
    }

    /**
     * Splits on commas, but respects quoted strings and brackets.
     */
    private fun splitRespectingQuotes(s: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var inBrackets = 0

        for (c in s) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                c == '[' && !inQuotes -> {
                    inBrackets++
                    current.append(c)
                }
                c == ']' && !inQuotes -> {
                    inBrackets--
                    current.append(c)
                }
                c == ',' && !inQuotes && inBrackets == 0 -> {
                    val trimmed = current.toString().trim()
                    if (trimmed.isNotEmpty()) parts.add(trimmed)
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) parts.add(last)
        return parts
    }
}

data class GoIRAnnotationFile(
    val annotations: List<GoIRAnnotation>,
    val hasSanityOnly: Boolean,
)

data class GoIRAnnotation(
    val line: Int,
    val kind: String,
    val function: String?,
    val args: Map<String, String>,
) {
    val instType: String? get() = args["instType"]
}
