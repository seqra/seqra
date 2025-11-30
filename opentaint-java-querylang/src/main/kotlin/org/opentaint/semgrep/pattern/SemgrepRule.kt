package org.opentaint.semgrep.pattern

sealed interface SemgrepRule<PatternsRepr> {
    fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr): SemgrepRule<NewRepr>
    fun <NewRepr> flatMap(block: (PatternsRepr) -> List<NewRepr>): SemgrepRule<NewRepr>
}

data class SemgrepTaintPropagator<PatternsRepr>(
    val from: String,
    val to: String,
    val pattern: PatternsRepr,
)

data class SemgrepTaintSource<PatternsRepr>(
    val label: String?,
    val requires: String?,
    val pattern: PatternsRepr,
)

data class SemgrepTaintSink<PatternsRepr>(
    val requires: String?,
    val pattern: PatternsRepr,
)

data class SemgrepTaintRule<PatternsRepr>(
    val sources: List<SemgrepTaintSource<PatternsRepr>>,
    val sinks: List<SemgrepTaintSink<PatternsRepr>>,
    val propagators: List<SemgrepTaintPropagator<PatternsRepr>>,
    val sanitizers: List<PatternsRepr>,
) : SemgrepRule<PatternsRepr> {
    override fun <NewRepr> transform(block: (PatternsRepr) -> NewRepr) =
        SemgrepTaintRule(
            sources = sources.map { SemgrepTaintSource(it.label, it.requires, block(it.pattern)) },
            sinks = sinks.map { SemgrepTaintSink(it.requires, block(it.pattern)) },
            propagators = propagators.map { SemgrepTaintPropagator(it.from, it.to, block(it.pattern)) },
            sanitizers = sanitizers.map(block),
        )

    override fun <NewRepr> flatMap(block: (PatternsRepr) -> List<NewRepr>) = SemgrepTaintRule(
        sources = sources.flatMap { p -> block(p.pattern).map { SemgrepTaintSource(p.label, p.requires, it) } },
        sinks = sinks.flatMap { p -> block(p.pattern).map { SemgrepTaintSink(p.requires, it) } },
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
