package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatFunctionKind
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.flat.operands
import org.opentaint.ir.impl.python.flat.target
import org.opentaint.ir.impl.python.flat.unpackTargets

/**
 * Pure analysis pass over a [FlatModuleIR]. Computes per-function
 * [ClosureInfo] (owned names, cells to allocate, closure variables to
 * receive from the parent) without modifying the IR.
 *
 * Bottom-up, no cycles: the parent map is a tree.
 */
object ClosureAnalyzer {

    fun analyze(module: FlatModuleIR): Map<String, ClosureInfo> {
        val allFunctions = collectAllFunctions(module)
        val byName: Map<String, FlatFunctionIR> = allFunctions.associateBy { it.qualifiedName }
        val parentMap: Map<String, String?> = buildClosureParentMap(module, byName)

        // Reverse: parent qn -> direct children qns.
        val children: Map<String, List<String>> = buildChildrenMap(parentMap)

        // Bottom-up memoized computation. Tracks both the public (override-applied)
        // and the propagated (pre-override) closureVars; parents read the propagated
        // form so a closure-root descendant doesn't block transitive capture.
        val publicCache = HashMap<String, ClosureInfo>()
        val propagatedClosureVars = HashMap<String, Set<String>>()

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

            val cellVars = childNeeds intersect ownedNames
            val propagated = directFree + (childNeeds - ownedNames)
            propagatedClosureVars[qn] = propagated

            val publicClosureVars = if (isClosureRoot(fn.kind)) emptySet() else propagated
            val info = ClosureInfo(
                ownedNames = ownedNames,
                cellVars = cellVars,
                closureVars = publicClosureVars,
                hasCapturingChildBind = false,   // filled in by the second pass below
            )
            publicCache[qn] = info
            return info
        }

        for (qn in byName.keys) compute(qn)

        // Second pass: now that every function's `closureVars` is finalised,
        // each function's `hasCapturingChildBind` follows from a bind-site walk.
        return publicCache.mapValues { (qn, info) ->
            val fn = byName.getValue(qn)
            info.copy(hasCapturingChildBind = functionHasCapturingChildBind(fn, publicCache))
        }
    }

    private fun functionHasCapturingChildBind(
        fn: FlatFunctionIR,
        infoCache: Map<String, ClosureInfo>,
    ): Boolean {
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                if (inst !is FlatBindFunction) continue
                val childInfo = infoCache[inst.function.qualifiedName] ?: continue
                if (childInfo.closureVars.isNotEmpty()) return true
            }
        }
        return false
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * A closure root cannot receive a closure environment from its parent —
     * Python's LEGB lookup rule means the function takes no implicit `<self>`
     * env at call time.
     *
     * **Caveat for `METHOD`** (see also [ClosureRewriter]'s diagnostic-bail
     * path at `FlatBindFunction` rewriting): CPython does NOT actually treat a
     * method body as terminating closure-cell flow. A method whose nested
     * function captures `x` from an enclosing function gets `x` in its own
     * `co_freevars` — the closure cell is threaded through the method at
     * `MAKE_FUNCTION` time (when the class body executes), bypassing the class
     * body itself. We model `METHOD` as a closure root here because:
     *
     *   1. It matches Python's surface semantics for *call-time* closure
     *      passing (methods don't take an implicit env argument), which is
     *      what our `<self>` parameter encodes.
     *   2. The shape that would expose the difference — a class defined
     *      inside a function body whose method contains a nested def
     *      capturing the enclosing function's local — is unreachable from
     *      real proto→Flat input today, because [FlatClass] does not
     *      represent class-inside-function (proto→Flat drops `CLASS_DEF`
     *      nested in function bodies).
     *
     * If/when class-inside-function support lands in `FlatClass`, this
     * decision should flip to CPython's behavior: drop `METHOD` from the
     * closure-root set when the method has descendants that need to pass
     * cells through (i.e. allocate `<self>` and a `_closure_env_` for the
     * method, and forward cells to its inner functions at *their* bind
     * sites). The rewriter today emits a `PIRDiagnostic` and bails on the
     * method when it encounters a `FlatBindFunction` for a capturing child
     * whose closure vars aren't in the method's `cellLocals` map.
     */
    private fun isClosureRoot(kind: FlatFunctionKind): Boolean = when (kind) {
        FlatFunctionKind.TOP_LEVEL,
        FlatFunctionKind.METHOD,
        FlatFunctionKind.MODULE_INIT -> true
        FlatFunctionKind.NESTED_DEF,
        FlatFunctionKind.LAMBDA -> false
    }

    private fun isSynthetic(name: String): Boolean =
        name.contains('$') || name.contains('<') || name.contains('>')

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
                inst.target?.let { addLocalName(it, out) }
                inst.unpackTargets.forEach { addLocalName(it, out) }
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
