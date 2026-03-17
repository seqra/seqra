package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.EllipsisArgumentPrefix
import org.opentaint.semgrep.pattern.MethodArguments
import org.opentaint.semgrep.pattern.NoArgs
import org.opentaint.semgrep.pattern.PatternArgumentPrefix
import org.opentaint.semgrep.pattern.SemgrepJavaPattern

fun createMethodArgs(args: List<SemgrepJavaPattern>): MethodArguments =
    args.foldRight(NoArgs as MethodArguments) { p, res -> PatternArgumentPrefix(p, res) }

fun parseMethodArgs(args: MethodArguments): List<SemgrepJavaPattern> = when (args) {
    is NoArgs -> emptyList()
    is EllipsisArgumentPrefix -> listOf(args) + parseMethodArgs(args.rest)
    is PatternArgumentPrefix -> listOf(args.argument) + parseMethodArgs(args.rest)
}
