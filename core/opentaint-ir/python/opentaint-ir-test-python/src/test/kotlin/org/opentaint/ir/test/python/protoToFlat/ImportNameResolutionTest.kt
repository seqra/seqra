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
import org.opentaint.ir.impl.python.flat.FlatModuleRef
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
import org.opentaint.ir.impl.python.transforms.closure.FlatClosureTransformer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for proto→Flat name resolution of `import` bindings.
 *
 * - `import os` (and aliases like `import os.path as p`) bind a module:
 *   they lower to [FlatModuleRef] carrying the canonical fullname; the
 *   alias is resolved away.
 * - `from os import getcwd` binds a value inside a module: it lowers to
 *   [FlatGlobalRef("getcwd", "os")].
 *
 * In both cases the bound name must NOT surface as [FlatLocal] — otherwise
 * the closure analyzer treats every imported name in every nested function
 * body as a fake capture, corrupting the downstream closure transform.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportNameResolutionTest : RawFlatModuleTestBase() {

    private fun localReads(fn: FlatFunctionIR): Set<String> {
        val out = HashSet<String>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                forEachOperand(inst) { v -> if (v is FlatLocal) out.add(v.name) }
            }
        }
        return out
    }

    /** Returns each FlatGlobalRef as `(simpleName, module)` derived from
     *  `qualifiedName` via last-dot split. */
    private fun globalRefs(fn: FlatFunctionIR): Set<Pair<String, String>> {
        val out = HashSet<Pair<String, String>>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                forEachOperand(inst) { v ->
                    if (v is FlatGlobalRef) {
                        val qn = v.qualifiedName
                        val dot = qn.lastIndexOf('.')
                        if (dot >= 0) out.add(qn.substring(dot + 1) to qn.substring(0, dot))
                        else out.add(qn to "")
                    }
                }
            }
        }
        return out
    }

    private fun moduleRefs(fn: FlatFunctionIR): Set<String> {
        val out = HashSet<String>()
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                forEachOperand(inst) { v -> if (v is FlatModuleRef) out.add(v.module) }
            }
        }
        return out
    }

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

    @Test
    fun `import os surfaces as FlatModuleRef in top-level function`() {
        val source = """
            import os

            def f():
                return os.getcwd()
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val modules = moduleRefs(f)
        val globals = globalRefs(f)
        assertFalse(
            "os" in locals,
            "'os' must not surface as FlatLocal. locals=$locals, modules=$modules",
        )
        assertTrue(
            "os" in modules,
            "expected FlatModuleRef(module=os). modules=$modules",
        )
        assertFalse(
            globals.any { it.first == "os" || it.second == "os" },
            "module name 'os' should not appear as a FlatGlobalRef. globals=$globals",
        )
    }

    @Test
    fun `from os import getcwd surfaces as FlatGlobalRef`() {
        val source = """
            from os import getcwd

            def f():
                return getcwd()
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val globals = globalRefs(f)
        assertFalse(
            "getcwd" in locals,
            "'getcwd' must not surface as FlatLocal. locals=$locals, globals=$globals",
        )
        assertTrue(
            globals.any { it.first == "getcwd" },
            "expected a FlatGlobalRef for 'getcwd'. globals=$globals",
        )
    }

    @Test
    fun `import alias surfaces as FlatModuleRef with canonical module name`() {
        val source = """
            import os.path as p

            def f():
                return p.join('a', 'b')
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val modules = moduleRefs(f)
        assertFalse(
            "p" in locals,
            "alias 'p' must not surface as FlatLocal. locals=$locals, modules=$modules",
        )
        assertTrue(
            "os.path" in modules,
            "alias 'p' should resolve to canonical module 'os.path'. modules=$modules",
        )
        assertFalse(
            "p" in modules,
            "alias 'p' should be discarded; canonical 'os.path' is the only ref. modules=$modules",
        )
    }

    @Test
    fun `nested def reading imported module produces no spurious capture`() {
        val source = """
            import os

            def outer():
                def inner():
                    return os.getcwd()
                return inner
        """
        val mod = lowerSourceToFlat(source)
        val transformed = FlatClosureTransformer.transform(mod)

        val inner = fn(transformed, ".outer\$inner")
        val outer = fn(transformed, ".outer")

        assertEquals(
            emptySet(),
            inner.closureVars,
            "inner.closureVars should be empty after closure transform; got ${inner.closureVars}",
        )
        assertEquals(
            emptySet(),
            outer.closureVars,
            "outer.closureVars should be empty after closure transform; got ${outer.closureVars}",
        )
        assertTrue(
            inner.parameters.none { it.name == "<self>" },
            "inner must not gain a synthetic <self> parameter; got ${inner.parameters.map { it.name }}",
        )
        assertEquals(
            emptyList(),
            transformed.diagnostics.map { it.message },
            "no closure diagnostics expected for an import-only nested def",
        )
        assertTrue(
            "os" in moduleRefs(inner),
            "expected 'os' to appear as a FlatModuleRef in inner; got ${moduleRefs(inner)}",
        )
    }

    @Test
    fun `from import getcwd as alias resolves to canonical FlatGlobalRef`() {
        val source = """
            from os import getcwd as g

            def f():
                return g()
        """
        val mod = lowerSourceToFlat(source)
        val f = fn(mod, ".f")

        val locals = localReads(f)
        val globals = globalRefs(f)
        assertFalse(
            "g" in locals,
            "alias 'g' must not surface as FlatLocal. locals=$locals, globals=$globals",
        )
        assertTrue(
            "getcwd" to "os" in globals,
            "alias 'g' should resolve to canonical FlatGlobalRef(getcwd, os); got globals=$globals",
        )
        assertFalse(
            globals.any { it.first == "g" },
            "alias 'g' should be discarded; only canonical name appears. globals=$globals",
        )
    }
}
