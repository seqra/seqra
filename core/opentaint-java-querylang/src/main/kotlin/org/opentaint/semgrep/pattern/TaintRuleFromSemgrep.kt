package org.opentaint.semgrep.pattern

data class TaintRuleFromSemgrep<R>(
    val ruleId: String,
    val root: Structure<R>,
    /** Creation claims captured before join branches and serialized items are flattened. */
    val markOwners: Map<String, String> = emptyMap(),
) {
    val taintRules: List<TaintRuleGroup<R>> get() = root.flatten()
    val size: Int get() = taintRules.sumOf { it.size }

    sealed interface Structure<R> {
        fun flatten(): List<TaintRuleGroup<R>>

        data class Matching<R>(val groups: List<TaintRuleGroup<R>>) : Structure<R> {
            override fun flatten(): List<TaintRuleGroup<R>> = groups
        }

        data class Taint<R>(
            val sources: List<TaintRuleGroup<R>>,
            val sinks: List<TaintRuleGroup<R>>,
            val propagators: List<TaintRuleGroup<R>>,
            val sanitizers: List<TaintRuleGroup<R>>,
        ) : Structure<R> {
            override fun flatten(): List<TaintRuleGroup<R>> = sources + sinks + propagators + sanitizers
        }

        data class Join<R>(val branches: List<JoinBranch<R>>) : Structure<R> {
            override fun flatten(): List<TaintRuleGroup<R>> = branches.flatMap { it.flatten() }
        }
    }

    data class JoinBranch<R>(
        val leftOperands: List<JoinOperand<R>>,
        val rightOperand: JoinOperand<R>,
    ) {
        fun flatten() = leftOperands.flatMap { it.flatten() } + rightOperand.flatten()
    }

    data class JoinOperand<R>(
        val itemId: String,
        val ruleId: String,
        val metavar: String,
        val child: Structure<R>,
    ) {
        fun flatten() = child.flatten()
    }

    data class TaintRuleGroup<R>(
        val rules: List<R>,
        val ruleDependencies: Map<String, Set<String>> = emptyMap(),
        val finalRuleIds: Set<String> = emptySet(),
    ) {
        val size: Int get() = rules.size
    }
}
