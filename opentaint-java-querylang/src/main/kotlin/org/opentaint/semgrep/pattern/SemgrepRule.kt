package org.opentaint.org.opentaint.semgrep.pattern

sealed interface SemgrepRule<PatternsRepr> {
    fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr): SemgrepRule<NewRepr>
    fun <NewRepr> flatMap(block: (PatternsRepr) -> List<NewRepr>): SemgrepRule<NewRepr>
}

data class SemgrepTaintPropagator<PatternsRepr>(
    val from: String,
    val to: String,
    val pattern: PatternsRepr,
)

data class SemgrepTaintRule<PatternsRepr>(
    val sources: List<PatternsRepr>,
    val sinks: List<PatternsRepr>,
    val propagators: List<SemgrepTaintPropagator<PatternsRepr>>,
    val sanitizers: List<PatternsRepr>,
) : SemgrepRule<PatternsRepr> {
    override fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr) =
        SemgrepTaintRule(
            sources = sources.map(block),
            sinks = sinks.map(block),
            propagators = propagators.map { SemgrepTaintPropagator(it.from, it.to, block(it.pattern)) },
            sanitizers = sanitizers.map(block),
        )

    override fun <NewRepr> flatMap(block: (PatternsRepr) -> List<NewRepr>) = SemgrepTaintRule(
        sources = sources.flatMap(block),
        sinks = sinks.flatMap(block),
        propagators = propagators.flatMap { p -> block(p.pattern).map { SemgrepTaintPropagator(p.from, p.to, it) } },
        sanitizers = sanitizers.flatMap(block),
    )
}

data class SemgrepMatchingRule<PatternsRepr>(
    val rules: List<PatternsRepr>,
) : SemgrepRule<PatternsRepr> {
    override fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr) =
        SemgrepMatchingRule(rules.map(block))

    override fun <NewRepr> flatMap(block: (PatternsRepr) -> List<NewRepr>) =
        SemgrepMatchingRule(rules.flatMap(block))
}
