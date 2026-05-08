package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.api.python.PIRPhysicalLocation
import org.opentaint.ir.impl.python.proto.MypyExprProto
import org.opentaint.ir.impl.python.proto.MypyStmtProto

/**
 * Build a [PIRPhysicalLocation] from a Mypy proto's `(line, col, end_line, end_col)`
 * tuple. Returns `null` unless the span is fully valid:
 *   - every coordinate is non-negative,
 *   - `end_line >= line`,
 *   - on the same line, `end_col >= col`. Equality is permitted because mypy's
 *     `end_column` is *exclusive* — a zero-width span is a legitimate empty
 *     node (e.g. an empty string literal or synthesized expression).
 *
 * A partial or inverted span is no span at all.
 */
internal fun MypyStmtProto.toPhysicalLocation(): PIRPhysicalLocation? =
    physicalLocationOf(line, col, endLine, endCol)

internal fun MypyExprProto.toPhysicalLocation(): PIRPhysicalLocation? =
    physicalLocationOf(line, col, endLine, endCol)

private fun physicalLocationOf(line: Int, col: Int, endLine: Int, endCol: Int): PIRPhysicalLocation? {
    if (line < 0 || col < 0 || endLine < 0 || endCol < 0) return null
    if (endLine < line) return null
    if (endLine == line && endCol < col) return null
    return PIRPhysicalLocation(
        lineStart = line.toUInt(),
        lineEnd = endLine.toUInt(),
        colStart = col.toUInt(),
        colEnd = endCol.toUInt(),
    )
}
