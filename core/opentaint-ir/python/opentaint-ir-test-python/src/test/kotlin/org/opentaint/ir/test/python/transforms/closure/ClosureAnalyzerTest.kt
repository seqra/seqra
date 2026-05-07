package org.opentaint.ir.test.python.transforms.closure

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.impl.python.flat.FlatAnyType
import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatBinOp
import org.opentaint.ir.impl.python.flat.FlatBinaryOperator
import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatBlock
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatCallArg
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatFunctionKind
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatInst
import org.opentaint.ir.impl.python.flat.FlatIntConst
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.flat.FlatParamKind
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatReturn
import org.opentaint.ir.impl.python.transforms.closure.ClosureAnalyzer
import kotlin.test.assertEquals

/**
 * Unit tests for [ClosureAnalyzer]. Hand-built [FlatModuleIR] fixtures —
 * does NOT go through proto-to-Flat lowering. The analyzer is pure
 * (no rewriting); these tests just verify the per-function fact extraction
 * and the bottom-up propagation formula.
 */
@Tag("tier2")
class ClosureAnalyzerTest {

    private val moduleName = "m"

    private fun fn(
        name: String,
        qualifiedName: String,
        parent: String?,
        kind: FlatFunctionKind,
        params: List<String> = emptyList(),
        body: List<FlatInst> = listOf(FlatReturn(null)),
        nonlocal: Set<String> = emptySet(),
        global: Set<String> = emptySet(),
    ): FlatFunctionIR {
        val parameters = params.map {
            FlatParameter(
                name = it,
                type = FlatAnyType,
                kind = FlatParamKind.POSITIONAL_OR_KEYWORD,
                hasDefault = false,
                defaultValue = null,
            )
        }
        val cfg = FlatCFG(
            blocks = listOf(FlatBlock(0, body, emptyList())),
            entryBlock = 0,
            exitBlocks = listOf(0),
        )
        return FlatFunctionIR(
            name = name,
            qualifiedName = qualifiedName,
            parentQualifiedName = parent,
            kind = kind,
            cfg = cfg,
            parameters = parameters,
            returnType = FlatAnyType,
            isAsync = false,
            isGenerator = false,
            decorators = emptyList(),
            nonlocalNames = nonlocal,
            globalNames = global,
        )
    }

    private fun moduleInit(): FlatFunctionIR =
        fn(
            name = "<module>",
            qualifiedName = "$moduleName.<module>",
            parent = null,
            kind = FlatFunctionKind.MODULE_INIT,
        )

    private fun module(
        functions: List<FlatFunctionIR>,
        classes: List<FlatClass> = emptyList(),
    ): FlatModuleIR = FlatModuleIR(
        moduleName = moduleName,
        path = "$moduleName.py",
        functions = functions,
        moduleInit = moduleInit(),
        classes = classes,
        fields = emptyList(),
        imports = emptyList(),
        diagnostics = emptyList<PIRDiagnostic>(),
    )

    private fun klass(
        name: String,
        qualifiedName: String,
        methods: List<FlatFunctionIR> = emptyList(),
        nestedClasses: List<FlatClass> = emptyList(),
    ) = FlatClass(
        name = name,
        qualifiedName = qualifiedName,
        baseClasses = emptyList(),
        mro = emptyList(),
        methods = methods,
        fields = emptyList(),
        nestedClasses = nestedClasses,
        decorators = emptyList(),
        isAbstract = false,
        isDataclass = false,
        isEnum = false,
    )

    private fun local(name: String) = FlatLocal(name)

    /* ------------------------------------------------------------------ */
    /* 1. simple capture                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    fun `simple capture`() {
        // def outer():
        //     x = 1
        //     def inner(): return x
        //     return inner
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("x"), FlatIntConst(1)),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(local("inner")),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, inner))).info

        assertEquals(setOf("x"), info.getValue(innerQn).closureVars)
        assertEquals(emptySet<String>(), info.getValue(innerQn).cellVars)
        assertEquals(setOf("x"), info.getValue(outerQn).cellVars)
        assertEquals(emptySet<String>(), info.getValue(outerQn).closureVars)
    }

    /* ------------------------------------------------------------------ */
    /* 2. nonlocal write                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    fun `nonlocal write`() {
        // def outer():
        //     count = 0
        //     def inc():
        //         nonlocal count
        //         count = count + 1
        val outerQn = "m.outer"
        val incQn = "m.outer.inc"

        val inc = fn(
            name = "inc",
            qualifiedName = incQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            nonlocal = setOf("count"),
            body = listOf(
                FlatBinOp(local("count"), local("count"), FlatIntConst(1), FlatBinaryOperator.ADD),
                FlatReturn(null),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("count"), FlatIntConst(0)),
                FlatBindFunction(local("inc"), FlatGlobalRef(incQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, inc))).info

        // `count` is in nonlocal, so it must propagate as a closure var even
        // though `inc` also writes it locally.
        assertEquals(setOf("count"), info.getValue(incQn).closureVars)
        // `inc` does not own `count` (nonlocal strips it from trueLocals).
        assertEquals(emptySet<String>(), info.getValue(incQn).cellVars)
        // `outer` owns `count` and has a child needing it.
        assertEquals(setOf("count"), info.getValue(outerQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 3. sibling reference                                               */
    /* ------------------------------------------------------------------ */

    @Test
    fun `sibling reference`() {
        // def outer():
        //     def b(): return 1
        //     def a(): return b()
        val outerQn = "m.outer"
        val aQn = "m.outer.a"
        val bQn = "m.outer.b"

        val b = fn(
            name = "b",
            qualifiedName = bQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(FlatIntConst(1))),
        )
        val a = fn(
            name = "a",
            qualifiedName = aQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(
                FlatCall(target = local("t0"), callee = local("b")),
                FlatReturn(local("t0")),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatBindFunction(local("b"), FlatGlobalRef(bQn)),
                FlatBindFunction(local("a"), FlatGlobalRef(aQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, a, b))).info

        assertEquals(setOf("b"), info.getValue(aQn).closureVars)
        assertEquals(setOf("b"), info.getValue(outerQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 4. transitive capture                                              */
    /* ------------------------------------------------------------------ */

    @Test
    fun `transitive capture`() {
        // def outer():
        //     x = 1
        //     def middle():
        //         def inner(): return x
        val outerQn = "m.outer"
        val middleQn = "m.outer.middle"
        val innerQn = "m.outer.middle.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = middleQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("x"))),
        )
        val middle = fn(
            name = "middle",
            qualifiedName = middleQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("x"), FlatIntConst(1)),
                FlatBindFunction(local("middle"), FlatGlobalRef(middleQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, middle, inner))).info

        assertEquals(setOf("x"), info.getValue(innerQn).closureVars)
        // Transitive: middle never directly references x but its descendant
        // does, so middle must receive it from outer.
        assertEquals(setOf("x"), info.getValue(middleQn).closureVars)
        assertEquals(emptySet<String>(), info.getValue(middleQn).cellVars)
        assertEquals(setOf("x"), info.getValue(outerQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 5. closure-root override TOP_LEVEL                                 */
    /* ------------------------------------------------------------------ */

    @Test
    fun `closure-root override TOP_LEVEL`() {
        // def f(): return unresolved
        val fQn = "m.f"
        val f = fn(
            name = "f",
            qualifiedName = fQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(FlatReturn(local("unresolved"))),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(f))).info
        assertEquals(emptySet<String>(), info.getValue(fQn).closureVars)
    }

    /* ------------------------------------------------------------------ */
    /* 6. closure-root override METHOD                                    */
    /* ------------------------------------------------------------------ */

    @Test
    fun `closure-root override METHOD`() {
        // class C:
        //     def m(self): return unresolved
        val cQn = "m.C"
        val mQn = "m.C.m"
        val mfn = fn(
            name = "m",
            qualifiedName = mQn,
            parent = null,
            kind = FlatFunctionKind.METHOD,
            params = listOf("self"),
            body = listOf(FlatReturn(local("unresolved"))),
        )
        val c = klass(name = "C", qualifiedName = cQn, methods = listOf(mfn))

        val info = ClosureAnalyzer.analyze(module(functions = emptyList(), classes = listOf(c))).info
        assertEquals(emptySet<String>(), info.getValue(mQn).closureVars)
    }

    /* ------------------------------------------------------------------ */
    /* 7. lambda capture                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    fun `lambda capture`() {
        // def outer():
        //     n = 1
        //     fn = lambda: n
        val outerQn = "m.outer"
        val lamQn = "m.outer.<lambda>"

        val lam = fn(
            name = "<lambda>",
            qualifiedName = lamQn,
            parent = outerQn,
            kind = FlatFunctionKind.LAMBDA,
            body = listOf(FlatReturn(local("n"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("n"), FlatIntConst(1)),
                FlatBindFunction(local("fn"), FlatGlobalRef(lamQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, lam))).info
        assertEquals(setOf("n"), info.getValue(lamQn).closureVars)
        assertEquals(setOf("n"), info.getValue(outerQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 8. multiple captures                                               */
    /* ------------------------------------------------------------------ */

    @Test
    fun `multiple captures`() {
        // def outer():
        //     a = 1; b = 2
        //     def inner(): return a + b
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(
                FlatBinOp(local("t0"), local("a"), local("b"), FlatBinaryOperator.ADD),
                FlatReturn(local("t0")),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("a"), FlatIntConst(1)),
                FlatAssign(local("b"), FlatIntConst(2)),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, inner))).info
        assertEquals(setOf("a", "b"), info.getValue(innerQn).closureVars)
        assertEquals(setOf("a", "b"), info.getValue(outerQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 9. non-capturing nested has empty closureVars                      */
    /* ------------------------------------------------------------------ */

    @Test
    fun `non-capturing nested has empty closureVars`() {
        // def outer():
        //     def inner(p):
        //         q = p
        //         return q
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
            body = listOf(
                FlatAssign(local("q"), local("p")),
                FlatReturn(local("q")),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, inner))).info
        assertEquals(emptySet<String>(), info.getValue(innerQn).closureVars)
        assertEquals(emptySet<String>(), info.getValue(outerQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 10. method-of-class-inside-function captures enclosing func local */
    /* ------------------------------------------------------------------ */

    @Test
    fun `method-of-class-inside-function captures enclosing function local`() {
        // def f():
        //     x = 1
        //     class C:
        //         def m(self):
        //             def inner(): return x
        //             return inner
        // FlatClass cannot today represent a class defined inside a function
        // body (proto-to-Flat drops class-defs nested in function bodies). To
        // exercise the rule that a nested def *inside* a method-of-a-class-
        // inside-a-function captures the enclosing function's local, the
        // analyzer treats the nested def's closure parent as the enclosing
        // function directly — i.e. it skips the method (METHOD is a closure
        // root) and the class scope. In hand-built fixtures we model this by
        // setting inner.parentQualifiedName to f (not m). When real
        // class-inside-function support lands, the analyzer's parent walk can
        // be extended to derive this skip automatically.
        val fQn = "m.f"
        val cQn = "m.f.C"
        val mQn = "m.f.C.m"
        val innerQn = "m.f.C.m.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = fQn, // skip method and class scope; see comment above.
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("x"))),
        )
        val mfn = fn(
            name = "m",
            qualifiedName = mQn,
            parent = null,
            kind = FlatFunctionKind.METHOD,
            params = listOf("self"),
            body = listOf(
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(local("inner")),
            ),
        )
        val c = klass(name = "C", qualifiedName = cQn, methods = listOf(mfn))
        val f = fn(
            name = "f",
            qualifiedName = fQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("x"), FlatIntConst(1)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(
            FlatModuleIR(
                moduleName = moduleName,
                path = "$moduleName.py",
                functions = listOf(f, inner),
                moduleInit = moduleInit(),
                classes = listOf(c),
                fields = emptyList(),
                imports = emptyList(),
                diagnostics = emptyList(),
            ),
        ).info

        assertEquals(setOf("x"), info.getValue(innerQn).closureVars)
        // Method is a closure root: public closureVars forced empty.
        assertEquals(emptySet<String>(), info.getValue(mQn).closureVars)
        // Method does not own x, so cellVars stays empty.
        assertEquals(emptySet<String>(), info.getValue(mQn).cellVars)
        // f owns x and a descendant needs it transitively.
        assertEquals(setOf("x"), info.getValue(fQn).cellVars)
    }

    /* ------------------------------------------------------------------ */
    /* 11. builtins are not free vars                                     */
    /* ------------------------------------------------------------------ */

    @Test
    fun `builtins are not free vars`() {
        // def outer():
        //     x = 1
        //     def inner():
        //         print(x); len(x)
        //
        // mypy resolves builtins to `FlatGlobalRef(name, "builtins")` during
        // proto-to-flat lowering, so by the time the analyzer sees the IR,
        // builtins are not `FlatLocal`s and never enter `refs`. The fixture
        // mirrors that reality.
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(
                FlatCall(
                    target = null,
                    callee = FlatGlobalRef("builtins.print"),
                    args = listOf(FlatCallArg(local("x"))),
                ),
                FlatCall(
                    target = null,
                    callee = FlatGlobalRef("builtins.len"),
                    args = listOf(FlatCallArg(local("x"))),
                ),
                FlatReturn(null),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("x"), FlatIntConst(1)),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, inner))).info
        val innerCv = info.getValue(innerQn).closureVars
        assertEquals(setOf("x"), innerCv)
        assert("print" !in innerCv)
        assert("len" !in innerCv)
    }

    @Test
    fun `transitive capture through method-in-class-inside-function`() {
        // def outer():
        //     x = 1
        //     class C:
        //         def m(self):
        //             def inner():
        //                 return x       # captures outer's x via m
        //             return inner()
        //     return C().m()
        //
        // The class-inside-function shape isn't producible from real
        // proto→Flat input today (FlatClass doesn't represent it), but the
        // analyzer is forward-compatible: when a method's `parent` points
        // at an enclosing function (instead of being null), the method is
        // *not* a closure root and forwards cells through. This is the
        // CPython-faithful behaviour.
        //
        // Closure-root status is now derived from "has no parent" rather
        // than from FlatFunctionKind, so a parented METHOD participates
        // in the closure chain like any other inner scope.
        val outerQn = "m.outer"
        val mQn = "m.outer.C.m"          // method, parent = outer
        val innerQn = "m.outer.C.m.inner" // nested def, parent = m

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = mQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("x"))),
        )
        val method = fn(
            name = "m",
            qualifiedName = mQn,
            parent = outerQn,
            kind = FlatFunctionKind.METHOD,
            params = listOf("self"),
            body = listOf(
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("x"), FlatIntConst(1)),
                FlatReturn(null),
            ),
        )

        val info = ClosureAnalyzer.analyze(module(listOf(outer, method, inner))).info

        // inner directly captures x from outer through m.
        assertEquals(setOf("x"), info.getValue(innerQn).closureVars)
        // m has a parent, so it is NOT a closure root: it forwards x.
        assertEquals(setOf("x"), info.getValue(mQn).closureVars)
        // m doesn't OWN x, so cellVars stays empty.
        assertEquals(emptySet(), info.getValue(mQn).cellVars)
        // outer owns x AND a transitive descendant needs it: must allocate cell.
        assertEquals(setOf("x"), info.getValue(outerQn).cellVars)
        // outer is parentless = closure root: closureVars forced to ∅.
        assertEquals(emptySet(), info.getValue(outerQn).closureVars)
    }
}
