package org.opentaint.org.opentaint.semgrep.pattern

sealed interface SemgrepRule<PatternsRepr> {
    fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr): SemgrepRule<NewRepr>
}

data class SemgrepTaintRule<PatternsRepr>(
    val sources: List<PatternsRepr>,
    val sinks: List<PatternsRepr>,
    val propagators: List<PatternsRepr>,
    val sanitizers: List<PatternsRepr>,
) : SemgrepRule<PatternsRepr> {
    override fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr) =
        SemgrepTaintRule(
            sources = sources.map(block),
            sinks = sinks.map(block),
            propagators = propagators.map(block),
            sanitizers = sanitizers.map(block),
        )
}

data class SemgrepMatchingRule<PatternsRepr>(
    val rule: PatternsRepr,
) : SemgrepRule<PatternsRepr> {
    override fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr) =
        SemgrepMatchingRule(block(rule))
}
