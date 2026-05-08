package org.opentaint.ir.api.python

/**
 * Source-level span of an instruction in the original Python file.
 *
 * Coordinate conventions, inherited from mypy:
 *   - lines are 1-based, both [lineStart] and [lineEnd] are inclusive;
 *   - columns are 0-based; [colStart] is inclusive, [colEnd] is exclusive
 *     (it is the column **after** the last character — mirrors Python's
 *     `ast.Node.end_col_offset`). A zero-width span (`colEnd == colStart` on
 *     the same line) is therefore legal and represents an empty node.
 *
 * Fields are [UInt] because line/column numbers are non-negative; the absence
 * of source information is represented by a `null` `physicalLocation` on the
 * owning instruction, not by sentinel values.
 */
data class PIRPhysicalLocation(
    val lineStart: UInt,
    val lineEnd: UInt,
    val colStart: UInt,
    val colEnd: UInt,
)
