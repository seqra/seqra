package org.opentaint.ir.test.python.protoToFlat

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatAwait
import org.opentaint.ir.impl.python.flat.FlatBinOp
import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatBranch
import org.opentaint.ir.impl.python.flat.FlatBuildDict
import org.opentaint.ir.impl.python.flat.FlatBuildList
import org.opentaint.ir.impl.python.flat.FlatBuildSet
import org.opentaint.ir.impl.python.flat.FlatBuildSlice
import org.opentaint.ir.impl.python.flat.FlatBuildString
import org.opentaint.ir.impl.python.flat.FlatBuildTuple
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatCompare
import org.opentaint.ir.impl.python.flat.FlatDeleteAttr
import org.opentaint.ir.impl.python.flat.FlatDeleteLocal
import org.opentaint.ir.impl.python.flat.FlatDeleteSubscript
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatGetIter
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatInst
import org.opentaint.ir.impl.python.flat.FlatLoadAttr
import org.opentaint.ir.impl.python.flat.FlatLoadSubscript
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.flat.FlatNextIter
import org.opentaint.ir.impl.python.flat.FlatRaise
import org.opentaint.ir.impl.python.flat.FlatReturn
import org.opentaint.ir.impl.python.flat.FlatStoreAttr
import org.opentaint.ir.impl.python.flat.FlatStoreGlobal
import org.opentaint.ir.impl.python.flat.FlatStoreSubscript
import org.opentaint.ir.impl.python.flat.FlatTypeCheck
import org.opentaint.ir.impl.python.flat.FlatUnaryOp
import org.opentaint.ir.impl.python.flat.FlatUnpack
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.flat.FlatYield
import org.opentaint.ir.impl.python.flat.FlatYieldFrom
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Experimental probe: how does mypy's name resolution surface in raw Flat IR?
 *
 * The closure analyzer assumes that anything mypy could resolve to a module
 * (globals, builtins, imports) lands as `FlatGlobalRef` and is therefore
 * invisible to closure analysis. The closure-root override exists partly as
 * defense for cases mypy *didn't* resolve. These tests measure how often
 * that defense actually fires, by walking each function's instructions and
 * collecting the names that surface as `FlatLocal` operands.
 *
 * Each test prints what it observes via assertion messages — failures are
 * the data, not a regression. Fix the assertion to match observation if
 * you're using these to learn what mypy does.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MypyResolutionProbeTest : RawFlatModuleTestBase() {

    /* ---------- helpers ---------- */

    /** Every name that appears as a `FlatLocal` *operand* (read position) in the function body. */
    private fun localReads(fn: FlatFunctionIR): Set<String> {
        val out = HashSet<String>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                addLocalReads(inst, out)
            }
        }
        return out
    }

    /** Every name that appears as a `FlatGlobalRef` operand (any position) in the function body. */
    private fun globalRefs(fn: FlatFunctionIR): Set<Pair<String, String>> {
        val out = HashSet<Pair<String, String>>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                addGlobalRefs(inst, out)
            }
        }
        return out
    }

    private fun addLocalReads(inst: FlatInst, out: MutableSet<String>) {
        forEachOperand(inst) { v -> if (v is FlatLocal) out.add(v.name) }
    }

    private fun addGlobalRefs(inst: FlatInst, out: MutableSet<Pair<String, String>>) {
        forEachOperand(inst) { v ->
            if (v is FlatGlobalRef) {
                val qn = v.qualifiedName
                val dot = qn.lastIndexOf('.')
                if (dot >= 0) out.add(qn.substring(dot + 1) to qn.substring(0, dot))
                else out.add(qn to "")
            }
        }
    }

    /** Walk every operand (read position) of [inst]. Mirrors `ClosureAnalyzer.addOperandReads`. */
    private inline fun forEachOperand(inst: FlatInst, f: (FlatValue) -> Unit) {
        when (inst) {
            is FlatAssign -> f(inst.source)
            is FlatBinOp -> { f(inst.left); f(inst.right) }
            is FlatUnaryOp -> f(inst.operand)
            is FlatCompare -> { f(inst.left); f(inst.right) }
            is FlatLoadAttr -> f(inst.obj)
            is FlatStoreAttr -> { f(inst.obj); f(inst.value) }
            is FlatLoadSubscript -> { f(inst.obj); f(inst.index) }
            is FlatStoreSubscript -> { f(inst.obj); f(inst.index); f(inst.value) }
            is FlatStoreGlobal -> f(inst.value)
            is FlatCall -> { f(inst.callee); inst.args.forEach { f(it.value) } }
            is FlatBuildList -> inst.elements.forEach(f)
            is FlatBuildTuple -> inst.elements.forEach(f)
            is FlatBuildSet -> inst.elements.forEach(f)
            is FlatBuildDict -> { inst.keys.forEach(f); inst.values.forEach(f) }
            is FlatBuildSlice -> { inst.lower?.let(f); inst.upper?.let(f); inst.step?.let(f) }
            is FlatBuildString -> inst.parts.forEach(f)
            is FlatGetIter -> f(inst.iterable)
            is FlatNextIter -> f(inst.iterator)
            is FlatBranch -> f(inst.condition)
            is FlatReturn -> inst.value?.let(f)
            is FlatRaise -> { inst.exception?.let(f); inst.cause?.let(f) }
            is FlatYield -> inst.value?.let(f)
            is FlatYieldFrom -> f(inst.iterable)
            is FlatAwait -> f(inst.awaitable)
            is FlatTypeCheck -> f(inst.value)
            is FlatUnpack -> f(inst.source)
            is FlatDeleteLocal -> f(inst.local)
            is FlatDeleteAttr -> f(inst.obj)
            is FlatDeleteSubscript -> { f(inst.obj); f(inst.index) }
            is FlatBindFunction -> Unit
            else -> Unit
        }
    }

    private fun allFunctions(module: FlatModuleIR): List<FlatFunctionIR> {
        fun classMethods(cls: FlatClass): List<FlatFunctionIR> =
            cls.methods + cls.nestedClasses.flatMap(::classMethods)
        return module.functions + module.moduleInit +
            module.classes.flatMap(::classMethods)
    }

    private fun fn(module: FlatModuleIR, qualifiedSuffix: String): FlatFunctionIR =
        allFunctions(module).first { it.qualifiedName.endsWith(qualifiedSuffix) }

    /* ---------- Probe 1: top-level function reading a module global ---------- */

    @Test
    fun `H1 module-level global referenced from top-level function`() {
        val source = """
            x = 42

            def reader():
                return x
        """
        val mod = lowerSourceToFlat(source)
        val reader = fn(mod, ".reader")

        val locals = localReads(reader)
        val globals = globalRefs(reader)
        // Hypothesis: mypy resolves `x` to module-level, so it's a FlatGlobalRef.
        assertTrue(
            "x" !in locals,
            "EXPECTED: 'x' is FlatGlobalRef (mypy resolved). " +
                "OBSERVED locals=$locals, globals=$globals",
        )
        assertTrue(
            globals.any { it.first == "x" },
            "EXPECTED: 'x' appears in globalRefs. globals=$globals",
        )
    }

    /* ---------- Probe 2: nested def captures an enclosing function local ---------- */

    @Test
    fun `H2 enclosing-function local referenced from nested def`() {
        val source = """
            def outer():
                value = 1
                def inner():
                        return value
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val inner = fn(mod, ".outer\$inner")

        val locals = localReads(inner)
        val globals = globalRefs(inner)
        // Hypothesis: enclosing-fn locals stay as FlatLocal (mypy doesn't dot-qualify them).
        assertTrue(
            "value" in locals,
            "EXPECTED: 'value' is FlatLocal (genuine capture). " +
                "OBSERVED locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 3: nested def reads a module global ---------- */

    @Test
    fun `H3 module-level global read from nested def`() {
        val source = """
            CONST = 99

            def outer():
                def inner():
                    return CONST
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val inner = fn(mod, ".outer\$inner")

        val locals = localReads(inner)
        val globals = globalRefs(inner)
        // Hypothesis: mypy resolves CONST to module-level → FlatGlobalRef.
        assertTrue(
            "CONST" !in locals,
            "EXPECTED: 'CONST' resolves to FlatGlobalRef even from nested. " +
                "OBSERVED locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 4: builtin reference ---------- */

    @Test
    fun `H4 builtin print referenced from top-level function`() {
        val source = """
            def f(x):
                return print(x)
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val globals = globalRefs(f)
        // Hypothesis: builtins resolve to FlatGlobalRef("print", "builtins").
        assertTrue(
            "print" !in locals,
            "EXPECTED: 'print' is FlatGlobalRef. OBSERVED locals=$locals, globals=$globals",
        )
        assertEquals(
            setOf("print" to "builtins"),
            globals.filter { it.first == "print" }.toSet(),
            "EXPECTED: print resolves to module 'builtins'. globals=$globals",
        )
    }

    /* ---------- Probe 5: method reading a module global ---------- */

    @Test
    fun `H5 module-level global referenced from method`() {
        val source = """
            CONFIG = 1

            class A:
                def m(self):
                    return CONFIG
        """
        val mod = lowerSourceToFlat(source)
        val m = fn(mod, ".A.m")

        val locals = localReads(m)
        val globals = globalRefs(m)
        assertTrue(
            "CONFIG" !in locals,
            "EXPECTED: 'CONFIG' is FlatGlobalRef from method. " +
                "OBSERVED locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 6: undefined / unbound name ---------- */

    @Test
    fun `H6 undefined name in top-level function`() {
        val source = """
            def f():
                return undefined_name
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val globals = globalRefs(f)
        // Open question: does mypy still attach a fullname for unresolved names?
        // If yes → globalRef; if no → FlatLocal (the override would matter here).
        // The failure message tells us which.
        assertTrue(
            true,
            "PROBE: undefined_name surfaced as locals=$locals, globals=$globals. " +
                "If 'undefined_name' is in locals, the closure-root override IS doing real work " +
                "for unbound-name cases. If in globals, mypy resolved it anyway.",
        )
        println(
            "[H6] undefined_name: locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 7: top-level function referencing another top-level function ---------- */

    @Test
    fun `H7 top-level function referenced from another top-level function`() {
        val source = """
            def helper():
                return 1

            def caller():
                return helper()
        """
        val mod = lowerSourceToFlat(source)
        val caller = fn(mod, ".caller")

        val locals = localReads(caller)
        val globals = globalRefs(caller)
        assertTrue(
            "helper" !in locals,
            "EXPECTED: 'helper' is FlatGlobalRef. OBSERVED locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 8: imported name ---------- */

    @Test
    fun `H8 imported name referenced from top-level function`() {
        val source = """
            import os

            def f():
                return os.getcwd()
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val globals = globalRefs(f)
        assertTrue(
            "os" !in locals,
            "EXPECTED: 'os' is FlatGlobalRef. OBSERVED locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 9: simulate Option 1a — recompute closureVars without the override ---------- */

    @Test
    fun `H9 closure analyzer with override removed - top-level reading global stays empty`() {
        val source = """
            x = 1
            def f():
                return x
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        // Simulate the analyzer's directFree computation without the override.
        // If mypy resolved 'x' to a global, this should be empty.
        val locals = localReads(f)
        val params = f.parameters.map { it.name }.toSet()
        val owned = params + collectLocalDefs(f)
        // BUILTIN_NAMES omitted on purpose — we want to see what's left.
        val directFree = locals - owned - f.globalNames
        assertTrue(
            directFree.isEmpty(),
            "Without the override, top-level f() would have directFree=$directFree. " +
                "If non-empty, the override is load-bearing for this case. " +
                "OBSERVED locals=$locals, globals=${globalRefs(f)}",
        )
    }

    /* ---------- Probe 10: same simulation, but for a method ---------- */

    @Test
    fun `H10 closure analyzer with override removed - method reading global stays empty`() {
        val source = """
            CONFIG = 1
            class A:
                def m(self):
                    return CONFIG
        """
        val mod = lowerSourceToFlat(source)
        val m = fn(mod, ".A.m")

        val locals = localReads(m)
        val params = m.parameters.map { it.name }.toSet()
        val owned = params + collectLocalDefs(m)
        val directFree = locals - owned - m.globalNames
        assertTrue(
            directFree.isEmpty(),
            "Without the METHOD override, A.m would have directFree=$directFree. " +
                "If non-empty, dropping the METHOD override breaks this case. " +
                "OBSERVED locals=$locals, globals=${globalRefs(m)}",
        )
    }

    /* ---------- Probe 11: full chain — outer.inner with mixed captures + globals ---------- */

    @Test
    fun `H11 nested def mixes a real capture with a real global`() {
        val source = """
            G = 100

            def outer():
                local_x = 1
                def inner():
                    return local_x + G
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val inner = fn(mod, ".outer\$inner")

        val locals = localReads(inner)
        val globals = globalRefs(inner)
        // We expect locals = {local_x}, globals contains G.
        assertEquals(
            setOf("local_x"),
            locals.filter { !it.startsWith("$") }.toSet(),
            "EXPECTED inner locals to be just {local_x} (G is a global). " +
                "OBSERVED locals=$locals, globals=$globals",
        )
        assertTrue(
            globals.any { it.first == "G" },
            "EXPECTED 'G' in globals. OBSERVED globals=$globals",
        )
    }

    /* ---------- Probe 12: nested def reads an imported module ---------- */

    @Test
    fun `H12 nested def reads imported name`() {
        val source = """
            import os

            def outer():
                def inner():
                    return os.getcwd()
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val inner = fn(mod, ".outer\$inner")

        val locals = localReads(inner)
        val globals = globalRefs(inner)
        // If 'os' is FlatLocal here, it propagates as a fake capture to outer.
        // The closure-root override on outer (TOP_LEVEL) clamps outer.closureVars to ∅,
        // but outer.cellVars = childNeeds ∩ ownedNames could still pick it up
        // if outer happened to have a local named `os`. The real test is: does the
        // formula without override emit a spurious capture for inner?
        assertTrue(
            "os" !in locals,
            "If 'os' appears in inner's FlatLocal reads, mypy did NOT resolve the import. " +
                "OBSERVED locals=$locals, globals=$globals",
        )
    }

    /* ---------- Probe 13: undefined name from a nested def — would the override save us? ---------- */

    @Test
    fun `H13 nested def references undefined name`() {
        val source = """
            def outer():
                def inner():
                    return undefined_thing
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val inner = fn(mod, ".outer\$inner")
        val outer = fn(mod, ".outer")

        val innerLocals = localReads(inner)
        val outerLocals = localReads(outer)
        // If 'undefined_thing' is FlatLocal in inner, inner.directFree contains it,
        // it propagates to outer.childNeeds, outer doesn't own it, so it lands in
        // outer.closureVars (before the override clamps to ∅). With the METHOD
        // override removed (Option 1a), this would fail to find a parent cell
        // and the rewriter would crash/diagnostic.
        println("[H13] inner FlatLocals=$innerLocals, outer FlatLocals=$outerLocals")
        // No assertion — observational only.
    }

    /* ---------- Probe 14: full pipeline — does importing os in outer trigger a diagnostic? ---------- */

    @Test
    fun `H14 import in outer with nested def using it triggers closure transformer`() {
        val source = """
            import os

            def outer():
                def inner():
                    return os.getcwd()
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val transformed = org.opentaint.ir.impl.python.transforms.closure.FlatClosureTransformer.transform(mod)
        val inner = fn(transformed, ".outer\$inner")
        val outer = fn(transformed, ".outer")

        println("[H14] inner.closureVars=${inner.closureVars}")
        println("[H14] outer.closureVars=${outer.closureVars}")
        println("[H14] inner.parameters=${inner.parameters.map { it.name }}")
        println("[H14] outer.parameters=${outer.parameters.map { it.name }}")
        println("[H14] diagnostics=${transformed.diagnostics.map { it.message }}")
    }

    /* ---------- collectLocalDefs (mirror of ClosureAnalyzer's logic, simplified) ---------- */

    private fun collectLocalDefs(fn: FlatFunctionIR): Set<String> {
        val out = HashSet<String>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                fun add(v: FlatValue?) { if (v is FlatLocal) out.add(v.name) }
                when (inst) {
                    is FlatAssign -> add(inst.target)
                    is FlatBinOp -> add(inst.target)
                    is FlatUnaryOp -> add(inst.target)
                    is FlatCompare -> add(inst.target)
                    is FlatLoadAttr -> add(inst.target)
                    is FlatLoadSubscript -> add(inst.target)
                    is org.opentaint.ir.impl.python.flat.FlatLoadGlobal -> add(inst.target)
                    is FlatBuildList -> add(inst.target)
                    is FlatBuildTuple -> add(inst.target)
                    is FlatBuildSet -> add(inst.target)
                    is FlatBuildDict -> add(inst.target)
                    is FlatBuildSlice -> add(inst.target)
                    is FlatBuildString -> add(inst.target)
                    is FlatGetIter -> add(inst.target)
                    is FlatTypeCheck -> add(inst.target)
                    is FlatCall -> inst.target?.let(::add)
                    is FlatYield -> inst.target?.let(::add)
                    is FlatYieldFrom -> inst.target?.let(::add)
                    is FlatAwait -> inst.target?.let(::add)
                    is FlatNextIter -> add(inst.target)
                    is FlatUnpack -> inst.targets.forEach(::add)
                    is FlatBindFunction -> add(inst.target)
                    else -> Unit
                }
            }
        }
        return out
    }
}
