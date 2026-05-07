package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.api.python.PIRDiagnosticSeverity
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.flat.operands
import org.opentaint.ir.impl.python.flat.targets

/**
 * Result of running [ClosureAnalyzer.analyze] over a module:
 *  - [info] — per-function [ClosureInfo] keyed by qualifiedName.
 *  - [diagnostics] — analyzer-emitted findings (e.g. parentless functions
 *    with non-empty propagated free names, which indicate either
 *    unresolved-name leaks from upstream or unsupported pass-through
 *    closure shapes).
 */
data class ClosureAnalysis(
    val info: Map<String, ClosureInfo>,
    val diagnostics: List<PIRDiagnostic>,
)

/**
 * Pure analysis pass over a [FlatModuleIR]. Computes per-function
 * [ClosureInfo] (owned names, cells to allocate, closure variables to
 * receive from the parent) without modifying the IR.
 *
 * Bottom-up, no cycles: the parent map is a tree.
 *
 * **Closure roots are parentless functions** — the parent map (built from
 * each function's `parentQualifiedName` plus the class-walk for methods)
 * is the single source of truth, not [FlatFunctionKind]. A parentless
 * function cannot receive a closure environment, so its `closureVars` is
 * forced to `∅`. If [collectLocalReads] surfaced any unresolved free name
 * for a parentless function, the analyzer emits a diagnostic — that
 * indicates either an unsupported pass-through shape (e.g. METHOD passing
 * cells through to a nested def in a class-inside-function) or an
 * unresolved-name leak from upstream lowering.
 */
object ClosureAnalyzer {

    fun analyze(module: FlatModuleIR): ClosureAnalysis {
        val allFunctions = collectAllFunctions(module)
        val byName: Map<String, FlatFunctionIR> = allFunctions.associateBy { it.qualifiedName }
        val parentMap: Map<String, String?> = buildClosureParentMap(module, byName)

        // Reverse: parent qn -> direct children qns.
        val children: Map<String, List<String>> = buildChildrenMap(parentMap)

        // Bottom-up memoized computation. Tracks both the public (override-applied)
        // and the propagated (pre-override) closureVars; parents read the propagated
        // form so a parentless descendant doesn't block transitive capture.
        val publicCache = HashMap<String, ClosureInfo>()
        val propagatedClosureVars = HashMap<String, Set<String>>()
        val diagnostics = ArrayList<PIRDiagnostic>()

        fun compute(qn: String): ClosureInfo {
            publicCache[qn]?.let { return it }
            val fn = byName.getValue(qn)

            val params = fn.parameters.map { it.name }.toSet()
            val localDefs = collectLocalDefs(fn)
            val refs = collectLocalReads(fn)

            val nonlocal = fn.nonlocalNames
            val global = fn.globalNames

            val trueLocals = localDefs - nonlocal - global
            val ownedNames = (params + trueLocals).filterNot(::isSynthetic).toSet()

            val directFree =
                ((refs - ownedNames - global).filterNot(::isSynthetic).toSet()) +
                    nonlocal

            val childQns = children[qn].orEmpty()
            // Recurse on children first so their propagatedClosureVars are populated.
            for (childQn in childQns) compute(childQn)
            val childNeeds: Set<String> = childQns
                .flatMap { propagatedClosureVars.getValue(it) }
                .toSet()

            // Keep deterministic iteration order on every set produced — the
            // rewriter emits prologue instructions and env-dict entries in
            // iteration order, and the PIR converter compares modules
            // structurally, so non-determinism here would surface as flaky
            // tests downstream.
            val cellVars = (childNeeds intersect ownedNames).sortedDeterministic()
            val propagated = (directFree + (childNeeds - ownedNames)).sortedDeterministic()
            propagatedClosureVars[qn] = propagated

            val isClosureRoot = parentMap[qn] == null
            if (isClosureRoot && propagated.isNotEmpty()) {
                // A parentless function with non-empty propagated free names
                // is suspect: either a descendant captures a name nobody owns
                // (e.g. METHOD pass-through to a nested def crossing a
                // class-inside-function boundary), or an upstream pass leaked
                // unresolved names into FlatLocal reads.
                diagnostics.add(
                    PIRDiagnostic(
                        severity = PIRDiagnosticSeverity.WARNING,
                        message = "Closure-root '$qn' has unresolved free names " +
                            "${propagated.toSortedSet()} — descendants capturing these " +
                            "names will not have cells forwarded.",
                        functionName = qn,
                        exceptionType = "ClosureRootLeak",
                    ),
                )
            }
            val publicClosureVars = if (isClosureRoot) emptySet() else propagated
            val info = ClosureInfo(
                ownedNames = ownedNames.sortedDeterministic(),
                cellVars = cellVars,
                closureVars = publicClosureVars,
                isClosureRoot = isClosureRoot,
            )
            publicCache[qn] = info
            return info
        }

        for (qn in byName.keys) compute(qn)
        return ClosureAnalysis(info = publicCache, diagnostics = diagnostics)
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private fun isSynthetic(name: String): Boolean =
        name.contains('$') || name.contains('<') || name.contains('>')

    /**
     * Returns a `Set<String>` whose iteration order is sorted ascending.
     * Implemented via a [LinkedHashSet] populated in sorted order so consumers
     * can rely on deterministic iteration without re-sorting at every use
     * site, while still satisfying the `Set` contract for membership.
     */
    private fun Set<String>.sortedDeterministic(): Set<String> =
        if (size <= 1) this else LinkedHashSet<String>(size).also { dst ->
            for (s in this.sorted()) dst.add(s)
        }

    private fun collectAllFunctions(module: FlatModuleIR): List<FlatFunctionIR> {
        val out = ArrayList<FlatFunctionIR>()
        out.add(module.moduleInit)
        out.addAll(module.functions)
        for (cls in module.classes) collectClassFunctions(cls, out)
        return out
    }

    private fun collectClassFunctions(cls: FlatClass, out: MutableList<FlatFunctionIR>) {
        out.addAll(cls.methods)
        for (nested in cls.nestedClasses) collectClassFunctions(nested, out)
    }

    /**
     * Build a `qualifiedName -> closureParentQualifiedName` map.
     *
     * Rules:
     * - Top-level functions and module init: parent = `null`.
     * - Methods of a class **inside a function** can in principle capture the
     *   enclosing function's locals through their nested defs/lambdas. The
     *   walker tracks the enclosing function as it descends into class bodies
     *   and nested classes and records `method.qn -> enclosingFunction`.
     *   Today, however, [FlatModuleIR.classes] cannot represent a class
     *   defined inside a function body (proto-to-Flat drops class-defs nested
     *   in function bodies), so this rule has no effect on real input — it is
     *   future-proofing. Until that gap is closed, a nested def lexically
     *   inside such a method must explicitly carry
     *   `parentQualifiedName = enclosingFunction.qualifiedName` to capture
     *   that function's locals (the analyzer's class-walk does NOT rewrite
     *   nested-def parents).
     * - Methods of top-level / module-level classes: parent = `null`
     *   (closure root).
     * - Nested defs / lambdas: parent is taken from
     *   [FlatFunctionIR.parentQualifiedName] (already correct: lexically
     *   enclosing function-like scope).
     */
    private fun buildClosureParentMap(
        module: FlatModuleIR,
        byName: Map<String, FlatFunctionIR>,
    ): Map<String, String?> {
        val map = HashMap<String, String?>()

        // Closure roots that live directly under the module.
        map[module.moduleInit.qualifiedName] = null

        for (fn in module.functions) {
            // For NESTED_DEF / LAMBDA the IR's parentQualifiedName already names
            // the enclosing function-like scope (which may be a method). For
            // TOP_LEVEL it's null. Trust it.
            map[fn.qualifiedName] = fn.parentQualifiedName
        }

        for (cls in module.classes) walkClass(cls, enclosingFunction = null, map = map)

        // Sanity: every parent referenced must be a known function. Drop unknown
        // parents (e.g. dangling refs) by mapping to null rather than crashing.
        return map.mapValues { (_, parent) -> if (parent != null && parent in byName) parent else null }
    }

    private fun walkClass(
        cls: FlatClass,
        enclosingFunction: String?,
        map: MutableMap<String, String?>,
    ) {
        for (method in cls.methods) {
            // Methods are closure roots on their own (closureVars forced empty),
            // but they may still own cells via descendants. Their closure parent
            // is the nearest enclosing function (skipping the class scope).
            map[method.qualifiedName] = enclosingFunction
        }
        for (nested in cls.nestedClasses) walkClass(nested, enclosingFunction, map)
    }

    private fun buildChildrenMap(parentMap: Map<String, String?>): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>()
        for ((child, parent) in parentMap) {
            if (parent == null) continue
            out.getOrPut(parent) { ArrayList() }.add(child)
        }
        return out
    }

    /**
     * Every name written via a `FlatLocal` target across all instructions in
     * the function. Includes single-target instructions, the multi-target
     * [org.opentaint.ir.impl.python.flat.FlatUnpack], and `FlatBindFunction`
     * (so sibling `def b()` references resolve).
     */
    private fun collectLocalDefs(fn: FlatFunctionIR): Set<String> {
        val out = HashSet<String>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                inst.targets.forEach { addLocalName(it, out) }
            }
        }
        return out
    }

    private fun addLocalName(value: FlatValue, out: MutableSet<String>) {
        if (value is FlatLocal) out.add(value.name)
    }

    /**
     * Every `FlatLocal` referenced as an *operand* across all instructions in
     * the function. `FlatBindFunction.function` is a name-binding target,
     * not an operand (per [org.opentaint.ir.impl.python.flat.mapOperand]'s
     * contract), so it contributes no reads here.
     */
    private fun collectLocalReads(fn: FlatFunctionIR): Set<String> {
        val out = HashSet<String>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                for (operand in inst.operands) addLocalName(operand, out)
            }
        }
        return out
    }
}
