package org.opentaint.org.opentaint.semgrep.pattern.conversion

data class SemgrepPatternActionList(
    val actions: List<SemgrepPatternAction>,
    val hasEllipsisInTheEnd: Boolean,
    val hasEllipsisInTheBeginning: Boolean,
)
