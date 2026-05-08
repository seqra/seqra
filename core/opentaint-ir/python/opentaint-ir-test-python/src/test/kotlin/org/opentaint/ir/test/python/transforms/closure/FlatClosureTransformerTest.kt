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
import org.opentaint.ir.impl.python.flat.FlatBuildDict
import org.opentaint.ir.impl.python.flat.FlatBuildList
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatCallArg
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatClassType
import org.opentaint.ir.impl.python.flat.FlatDecorator
import org.opentaint.ir.impl.python.flat.FlatDeleteAttr
import org.opentaint.ir.impl.python.flat.FlatDeleteLocal
import org.opentaint.ir.impl.python.flat.FlatExceptHandler
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatFunctionKind
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatInst
import org.opentaint.ir.impl.python.flat.FlatIntConst
import org.opentaint.ir.impl.python.flat.FlatLoadAttr
import org.opentaint.ir.impl.python.flat.FlatLoadSubscript
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import org.opentaint.ir.impl.python.flat.FlatNextIter
import org.opentaint.ir.impl.python.flat.FlatParamKind
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatParameterRef
import org.opentaint.ir.impl.python.flat.FlatReturn
import org.opentaint.ir.impl.python.flat.FlatStoreAttr
import org.opentaint.ir.impl.python.flat.FlatStrConst
import org.opentaint.ir.impl.python.flat.FlatUnpack
import org.opentaint.ir.impl.python.flat.FlatValue
import org.opentaint.ir.impl.python.transforms.closure.ClosureRuntime
import org.opentaint.ir.impl.python.transforms.closure.FlatClosureTransformer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for [FlatClosureTransformer]: rewriter on top of analyzer.
 * Hand-built [FlatModuleIR] fixtures — does NOT go through proto-to-Flat.
 */
@Tag("tier2")
class FlatClosureTransformerTest {

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
        decorators: List<FlatDecorator> = emptyList(),
        blocks: List<FlatBlock>? = null,
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
        val cfg = if (blocks != null) {
            FlatCFG(blocks = blocks, entryBlock = blocks.first().label, exitBlocks = listOf(blocks.last().label))
        } else {
            FlatCFG(
                blocks = listOf(FlatBlock(0, body, emptyList())),
                entryBlock = 0,
                exitBlocks = listOf(0),
            )
        }
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
            decorators = decorators,
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
    ) = FlatClass(
        name = name,
        qualifiedName = qualifiedName,
        baseClasses = emptyList(),
        mro = emptyList(),
        methods = methods,
        fields = emptyList(),
        nestedClasses = emptyList(),
        decorators = emptyList(),
        isAbstract = false,
        isDataclass = false,
        isEnum = false,
    )

    private fun local(name: String) = FlatLocal(name)

    private fun cellName(n: String) = "\$cell\$$n"

    /**
     * Locate the rewritten function by qualified name. Capturing impls are
     * renamed to `module.<closure_$base_impl>` per the callable-shim refactor,
     * so a lookup for the original `module.outer.inner` qn falls back to
     * searching for a function whose `name` matches the impl-rename pattern
     * `<closure_$base_impl>` for the original short name.
     */
    private fun lookup(out: FlatModuleIR, qn: String): FlatFunctionIR {
        out.functions.firstOrNull { it.qualifiedName == qn }?.let { return it }
        if (out.moduleInit.qualifiedName == qn) return out.moduleInit
        for (c in out.classes) {
            c.methods.firstOrNull { it.qualifiedName == qn }?.let { return it }
        }
        // Fallback: capturing impl was renamed.
        val baseName = qn.substringAfterLast('.')
        val implName = "<closure_${baseName}_impl>"
        out.functions.firstOrNull { it.name == implName }?.let { return it }
        error("Function $qn not found in rewritten module")
    }

    /** Find the synthesized adapter class for a capturing impl by its base name. */
    private fun adapter(out: FlatModuleIR, baseName: String): FlatClass {
        val expected = "<closure_$baseName>"
        return out.classes.firstOrNull { it.name == expected }
            ?: error("Adapter class $expected not found in rewritten module")
    }

    private fun entryInsts(fn: FlatFunctionIR): List<FlatInst> =
        fn.cfg.blocks.first { it.label == fn.cfg.entryBlock }.instructions

    /* ------------------------------------------------------------------ */
    /* 1. <self> injected iff closureVars non-empty                       */
    /* ------------------------------------------------------------------ */

    @Test
    fun `self injected when closureVars non-empty`() {
        // outer has x; inner reads x. inner must get <self> param at index 0.
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"

        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
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
                FlatReturn(null),
            ),
        )

        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenInner = lookup(out, innerQn)
        assertEquals(ClosureRuntime.SELF_PARAM_NAME, rewrittenInner.parameters[0].name)
        assertEquals("p", rewrittenInner.parameters[1].name)
        assertEquals(setOf("x"), rewrittenInner.closureVars)
    }

    /* ------------------------------------------------------------------ */
    /* 2. Non-capturing nested has no <self>                              */
    /* ------------------------------------------------------------------ */

    @Test
    fun `non-capturing nested has no self`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
            body = listOf(FlatReturn(local("p"))),
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

        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenInner = lookup(out, innerQn)
        assertEquals(listOf("p"), rewrittenInner.parameters.map { it.name })
        assertEquals(emptySet(), rewrittenInner.closureVars)
    }

    /* ------------------------------------------------------------------ */
    /* 3. Prologue contains __pir_cell__() calls for each cellVar          */
    /* ------------------------------------------------------------------ */

    @Test
    fun `prologue contains pir_cell calls for each cellVar`() {
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

        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenOuter = lookup(out, outerQn)
        val insts = entryInsts(rewrittenOuter)
        // First two should be __pir_cell__() calls.
        val cellCtorQn = "builtins.${ClosureRuntime.CELL_CTOR_NAME}"
        val cellCalls = insts.filterIsInstance<FlatCall>().filter {
            (it.callee as? FlatGlobalRef)?.qualifiedName == cellCtorQn
        }
        assertEquals(2, cellCalls.size)
        val targets = cellCalls.mapNotNull { (it.target as? FlatLocal)?.name }.toSet()
        assertEquals(setOf(cellName("a"), cellName("b")), targets)
        // First instruction in body is a cell allocation (prologue prepended).
        val firstCellAllocAt = insts.indexOfFirst {
            it is FlatCall && (it.callee as? FlatGlobalRef)?.qualifiedName == cellCtorQn
        }
        assertEquals(0, firstCellAllocAt)
    }

    /* ------------------------------------------------------------------ */
    /* 4. Parameter cells seeded                                          */
    /* ------------------------------------------------------------------ */

    @Test
    fun `parameter cells seeded`() {
        // outer(x): def inner(): return x  → outer.cellVars = {x}, x is param
        //
        // The fixture mirrors what `CfgBuild.buildFunctionCfg` produces: the
        // function-entry parameter-binding prologue (`FlatAssign(FlatLocal(x),
        // FlatParameterRef(x))`) is the body's first instruction. The closure
        // rewriter's `defaultRewrite` redirects writes into a cell-managed
        // `FlatLocal(x)` target through a fresh temp + `FlatStoreAttr`, which
        // is what seeds `$cell$x` from the parameter — no explicit seed is
        // emitted by the rewriter's prologue. We assert exactly that shape.
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
            params = listOf("x"),
            body = listOf(
                FlatAssign(local("x"), FlatParameterRef("x")),  // mirrors CfgBuild's prologue
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )

        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenOuter = lookup(out, outerQn)
        val insts = entryInsts(rewrittenOuter)

        // Expect FlatCall(__pir_cell__, target=$cell$x) somewhere in the
        // entry block, followed (after the param-binding prologue's redirected
        // assign) by a FlatStoreAttr($cell$x, "value", _) seeding the cell.
        val allocIdx = insts.indexOfFirst {
            it is FlatCall &&
                (it.callee as? FlatGlobalRef)?.qualifiedName == "builtins.${ClosureRuntime.CELL_CTOR_NAME}" &&
                (it.target as? FlatLocal)?.name == cellName("x")
        }
        assertTrue(allocIdx >= 0, "expected cell-alloc for x; insts=$insts")

        // The redirected param-binding prologue produces:
        //   FlatAssign($tcN, FlatParameterRef("x"))
        //   FlatStoreAttr($cell$x, "value", $tcN)
        // somewhere after the cell-alloc. Find the FlatStoreAttr targeting
        // $cell$x and verify its value chains back to the parameter-ref.
        val seedStore = insts.drop(allocIdx + 1).filterIsInstance<FlatStoreAttr>().firstOrNull { s ->
            (s.obj as? FlatLocal)?.name == cellName("x") &&
                s.attribute == ClosureRuntime.CELL_VALUE_ATTR
        }
        assertNotNull(seedStore, "expected FlatStoreAttr seeding ${cellName("x")}; insts=$insts")
        val seedTemp = (seedStore!!.value as FlatLocal).name
        val seedAssign = insts.filterIsInstance<FlatAssign>().firstOrNull {
            (it.target as? FlatLocal)?.name == seedTemp && it.source is FlatParameterRef
        }
        assertNotNull(seedAssign,
            "expected FlatAssign($seedTemp, FlatParameterRef(\"x\")) preceding the seed store; insts=$insts")
        assertEquals("x", (seedAssign!!.source as FlatParameterRef).name)
    }

    /* ------------------------------------------------------------------ */
    /* 5. Body reads through FlatLoadAttr; substitution flows downstream   */
    /* ------------------------------------------------------------------ */

    @Test
    fun `body reads through FlatLoadAttr and substitution flows`() {
        // def outer():
        //     x = 1
        //     def inner(): return x
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
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenInner = lookup(out, innerQn)
        val insts = entryInsts(rewrittenInner)
        // Skip the prologue. Find the FlatReturn — the value should be a temp loaded
        // from $cell$x via FlatLoadAttr immediately preceding it.
        val ret = insts.last() as FlatReturn
        val retVal = ret.value as FlatLocal
        assertTrue(retVal.name.startsWith("\$t"), "Return value should be a fresh temp, got ${retVal.name}")
        // The instruction immediately before should be FlatLoadAttr targeting that temp.
        val loadIdx = insts.indexOfFirst {
            it is FlatLoadAttr &&
                (it.target as? FlatLocal)?.name == retVal.name &&
                (it.obj as? FlatLocal)?.name == cellName("x") &&
                it.attribute == ClosureRuntime.CELL_VALUE_ATTR
        }
        assertTrue(loadIdx >= 0, "No matching FlatLoadAttr found in: $insts")
    }

    /* ------------------------------------------------------------------ */
    /* 6. Body writes go through FlatStoreAttr for various inst kinds      */
    /* ------------------------------------------------------------------ */

    @Test
    fun `body writes through FlatStoreAttr - FlatBinOp target captured`() {
        // count = count + 1, with count captured (nonlocal write).
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
        val out = FlatClosureTransformer.transform(module(listOf(outer, inc)))
        val rewrittenInc = lookup(out, incQn)
        val insts = entryInsts(rewrittenInc)
        // Find the FlatBinOp. Its target must be a $tN temp (not "count"), and a
        // FlatStoreAttr($cell$count, "value", $tN) must immediately follow.
        val binIdx = insts.indexOfFirst { it is FlatBinOp }
        assertTrue(binIdx >= 0)
        val bin = insts[binIdx] as FlatBinOp
        val tempName = (bin.target as FlatLocal).name
        assertTrue(tempName.startsWith("\$t"))
        val store = insts[binIdx + 1] as FlatStoreAttr
        assertEquals(cellName("count"), (store.obj as FlatLocal).name)
        assertEquals(ClosureRuntime.CELL_VALUE_ATTR, store.attribute)
        assertEquals(tempName, (store.value as FlatLocal).name)
    }

    @Test
    fun `body writes through FlatStoreAttr - FlatCall target captured`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            nonlocal = setOf("v"),
            body = listOf(
                FlatCall(target = local("v"), callee = local("f"), args = emptyList()),
                FlatReturn(null),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            params = listOf("f"),
            body = listOf(
                FlatAssign(local("v"), FlatIntConst(0)),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val insts = entryInsts(lookup(out, innerQn))
        val callIdx = insts.indexOfFirst { it is FlatCall && (it as FlatCall).target != null }
        val call = insts[callIdx] as FlatCall
        val tempName = (call.target as FlatLocal).name
        assertTrue(tempName.startsWith("\$t"))
        val store = insts[callIdx + 1] as FlatStoreAttr
        assertEquals(cellName("v"), (store.obj as FlatLocal).name)
    }

    @Test
    fun `body writes through FlatStoreAttr - FlatBuildList target captured`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            nonlocal = setOf("xs"),
            body = listOf(
                FlatBuildList(target = local("xs"), elements = listOf(FlatIntConst(1), FlatIntConst(2))),
                FlatReturn(null),
            ),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(local("xs"), FlatIntConst(0)),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val insts = entryInsts(lookup(out, innerQn))
        val buildIdx = insts.indexOfFirst { it is FlatBuildList }
        val build = insts[buildIdx] as FlatBuildList
        val tempName = (build.target as FlatLocal).name
        assertTrue(tempName.startsWith("\$t"))
        val store = insts[buildIdx + 1] as FlatStoreAttr
        assertEquals(cellName("xs"), (store.obj as FlatLocal).name)
        assertEquals(tempName, (store.value as FlatLocal).name)
    }

    @Test
    fun `body writes through FlatStoreAttr - FlatNextIter target captured`() {
        // for-loop where the loop variable is captured by an inner def.
        // Only build the structure we need to inspect for FlatNextIter rewrite.
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("i"))),
        )
        // Manually build a CFG with FlatNextIter writing to `i`.
        val entryBlock = FlatBlock(
            label = 0,
            instructions = listOf(
                FlatAssign(local("it"), FlatIntConst(0)), // pretend iterator
                FlatNextIter(target = local("i"), iterator = local("it"), bodyBlock = 1, exitBlock = 2),
            ),
            exceptionHandlers = emptyList(),
        )
        val bodyBlock = FlatBlock(
            label = 1,
            instructions = listOf(
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                org.opentaint.ir.impl.python.flat.FlatGoto(0),
            ),
            exceptionHandlers = emptyList(),
        )
        val exitBlock = FlatBlock(
            label = 2,
            instructions = listOf(FlatReturn(null)),
            exceptionHandlers = emptyList(),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            blocks = listOf(entryBlock, bodyBlock, exitBlock),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenOuter = lookup(out, outerQn)
        val rewrittenEntry = rewrittenOuter.cfg.blocks.first { it.label == 0 }.instructions
        val nextIdx = rewrittenEntry.indexOfFirst { it is FlatNextIter }
        val nextInst = rewrittenEntry[nextIdx] as FlatNextIter
        val tempName = (nextInst.target as FlatLocal).name
        assertTrue(tempName.startsWith("\$t"))
        val store = rewrittenEntry[nextIdx + 1] as FlatStoreAttr
        assertEquals(cellName("i"), (store.obj as FlatLocal).name)
        assertEquals(tempName, (store.value as FlatLocal).name)
    }

    @Test
    fun `body writes through FlatStoreAttr - FlatExceptHandler target captured`() {
        // try: ... except E as e: ... where e is captured by inner.
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("e"))),
        )
        val handlerBlock = FlatBlock(
            label = 1,
            instructions = listOf(
                FlatExceptHandler(target = local("e"), exceptionTypes = listOf(FlatClassType("Exception"))),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
            exceptionHandlers = emptyList(),
        )
        val entryBlock = FlatBlock(
            label = 0,
            instructions = listOf(org.opentaint.ir.impl.python.flat.FlatGoto(1)),
            exceptionHandlers = emptyList(),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            blocks = listOf(entryBlock, handlerBlock),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenOuter = lookup(out, outerQn)
        val handlerInsts = rewrittenOuter.cfg.blocks.first { it.label == 1 }.instructions
        val hIdx = handlerInsts.indexOfFirst { it is FlatExceptHandler }
        val h = handlerInsts[hIdx] as FlatExceptHandler
        val tempName = (h.target as FlatLocal).name
        assertTrue(tempName.startsWith("\$t"))
        val store = handlerInsts[hIdx + 1] as FlatStoreAttr
        assertEquals(cellName("e"), (store.obj as FlatLocal).name)
        assertEquals(tempName, (store.value as FlatLocal).name)
    }

    @Test
    fun `body writes through FlatStoreAttr - FlatUnpack mixed cell-managed and not`() {
        // a, b = pair  where a is captured but b is not.
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(FlatReturn(local("a"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            params = listOf("pair"),
            body = listOf(
                FlatUnpack(targets = listOf(local("a"), local("b")), source = local("pair"), starIndex = -1),
                FlatBindFunction(local("inner"), FlatGlobalRef(innerQn)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val insts = entryInsts(lookup(out, outerQn))
        val unpackIdx = insts.indexOfFirst { it is FlatUnpack }
        val unpack = insts[unpackIdx] as FlatUnpack
        val a = unpack.targets[0] as FlatLocal
        val b = unpack.targets[1] as FlatLocal
        assertTrue(a.name.startsWith("\$t"), "Captured slot should be redirected to temp, got ${a.name}")
        assertEquals("b", b.name, "Non-captured slot should be unchanged")
        // After the unpack, expect a FlatStoreAttr for $cell$a from the temp.
        val storeIdx = unpackIdx + 1
        val store = insts[storeIdx] as FlatStoreAttr
        assertEquals(cellName("a"), (store.obj as FlatLocal).name)
        assertEquals(a.name, (store.value as FlatLocal).name)
    }

    /* ------------------------------------------------------------------ */
    /* 7. FlatDeleteLocal of captured name → FlatDeleteAttr                */
    /* ------------------------------------------------------------------ */

    @Test
    fun `delete local of captured name lowers to delete attr`() {
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
                FlatDeleteLocal(local("x")),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val insts = entryInsts(lookup(out, outerQn))
        // Original FlatDeleteLocal must be gone; a FlatDeleteAttr on $cell$x must appear.
        assertFalse(insts.any { it is FlatDeleteLocal }, "FlatDeleteLocal(x) should be lowered")
        val del = insts.filterIsInstance<FlatDeleteAttr>().firstOrNull {
            (it.obj as? FlatLocal)?.name == cellName("x") && it.attribute == ClosureRuntime.CELL_VALUE_ATTR
        }
        assertNotNull(del, "Expected FlatDeleteAttr on \$cell\$x")
    }

    /* ------------------------------------------------------------------ */
    /* 8. Bind sites for capturing children emit BuildDict + StoreAttr     */
    /* ------------------------------------------------------------------ */

    @Test
    fun `bind site for capturing child emits build dict and constructor call`() {
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
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val insts = entryInsts(lookup(out, outerQn))
        // Capturing-child bind site is replaced by FlatBuildDict + FlatCall(adapter ctor).
        // No FlatBindFunction should remain at this site (callable-shim shape).
        assertFalse(
            insts.any { it is FlatBindFunction },
            "Capturing-child bind should be rewritten to a constructor call",
        )
        val buildIdx = insts.indexOfFirst { it is FlatBuildDict }
        assertTrue(buildIdx >= 0)
        val buildDict = insts[buildIdx] as FlatBuildDict
        assertEquals(listOf(FlatStrConst("x") as FlatValue), buildDict.keys)
        assertEquals(listOf(local(cellName("x")) as FlatValue), buildDict.values)
        // Next is the constructor call producing the adapter instance into `inner`.
        val ctor = insts[buildIdx + 1] as FlatCall
        assertEquals("inner", (ctor.target as FlatLocal).name)
        val callee = ctor.callee as FlatGlobalRef
        assertEquals("$moduleName.<closure_inner>", callee.qualifiedName)
        assertEquals(1, ctor.args.size)
        assertEquals((buildDict.target as FlatLocal).name, (ctor.args[0].value as FlatLocal).name)
    }

    @Test
    fun `capturing nested def emits adapter class with init and call`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
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
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val cls = adapter(out, "inner")
        // Synthetic name uses angle brackets — invalid Python identifier, no collision.
        assertTrue(cls.name.contains('<') && cls.name.contains('>'),
            "Adapter class name should contain angle brackets, got ${cls.name}")
        assertEquals("$moduleName.${cls.name}", cls.qualifiedName)
        assertEquals(2, cls.methods.size)
        assertEquals("__init__", cls.methods[0].name)
        assertEquals("__call__", cls.methods[1].name)

        // __init__: stores _closure_env_ on self.
        val initInsts = cls.methods[0].cfg.blocks.first().instructions
        assertEquals(listOf("self", ClosureRuntime.CLOSURE_ATTR_NAME), cls.methods[0].parameters.map { it.name })
        val store = initInsts.filterIsInstance<FlatStoreAttr>().single()
        assertEquals(ClosureRuntime.CLOSURE_ATTR_NAME, store.attribute)
        assertEquals("self", (store.obj as FlatLocal).name)
        assertEquals(ClosureRuntime.CLOSURE_ATTR_NAME, (store.value as FlatLocal).name)

        // __call__: forwards self + p positionally to impl.
        val callMethod = cls.methods[1]
        assertEquals(listOf("self", "p"), callMethod.parameters.map { it.name })
        val callInsts = callMethod.cfg.blocks.first().instructions
        val implCall = callInsts.filterIsInstance<FlatCall>().single()
        assertEquals(2, implCall.args.size)
        assertEquals("self", (implCall.args[0].value as FlatLocal).name)
        assertEquals("p", (implCall.args[1].value as FlatLocal).name)
        // Impl callee is a FlatGlobalRef pointing at the renamed impl.
        val implCallee = implCall.callee as FlatGlobalRef
        assertEquals("$moduleName.<closure_inner_impl>", implCallee.qualifiedName)
    }

    @Test
    fun `non-capturing nested def emits no adapter class and no impl rename`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
            body = listOf(FlatReturn(local("p"))),
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
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        // No adapter class emitted.
        assertTrue(
            out.classes.none { it.name.startsWith("<closure_") },
            "Non-capturing child should not synthesize an adapter class",
        )
        // Impl keeps its original qualified name.
        assertNotNull(out.functions.firstOrNull { it.qualifiedName == innerQn })
    }

    /* ------------------------------------------------------------------ */
    /* 9. Bind sites for non-capturing children emit no env attach         */
    /* ------------------------------------------------------------------ */

    @Test
    fun `bind site for non-capturing child has no env attach`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
            body = listOf(FlatReturn(local("p"))),
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
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenOuter = lookup(out, outerQn)
        // Since outer has no cells and no capturing children, function should be untouched.
        // Either way: no FlatStoreAttr with CLOSURE_ATTR_NAME may exist anywhere in body.
        val insts = entryInsts(rewrittenOuter)
        assertFalse(
            insts.any {
                it is FlatStoreAttr && it.attribute == ClosureRuntime.CLOSURE_ATTR_NAME
            },
            "Non-capturing child should not trigger env attach",
        )
        assertFalse(
            insts.any { it is FlatBuildDict },
            "Non-capturing child should not trigger env build",
        )
    }

    /* ------------------------------------------------------------------ */
    /* 10. Calls to closure-bearing children pass only user args           */
    /* ------------------------------------------------------------------ */

    @Test
    fun `calls to closure bearing children pass only user args`() {
        // outer creates inner (which captures x), then calls inner(42).
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf("p"),
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
                FlatCall(target = local("r"), callee = local("inner"), args = listOf(FlatCallArg(FlatIntConst(42)))),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val insts = entryInsts(lookup(out, outerQn))
        // Find the user call: callee is FlatLocal("inner") (the adapter instance).
        val userCall = insts.filterIsInstance<FlatCall>().firstOrNull {
            val callee = it.callee
            callee is FlatLocal && callee.name == "inner"
        }
        assertNotNull(userCall)
        // Only one user-provided argument: 42. No implicit <self> at the call site.
        assertEquals(1, userCall!!.args.size)
        assertEquals(FlatIntConst(42), userCall.args[0].value)
    }

    /* ------------------------------------------------------------------ */
    /* 11. Decorated nested def: bind emitted, decorator metadata kept     */
    /* ------------------------------------------------------------------ */

    @Test
    fun `decorated nested def keeps decorators and binds`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val deco = FlatDecorator(name = "mydeco", qualifiedName = "m.mydeco", arguments = emptyList())
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            decorators = listOf(deco),
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
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val rewrittenInner = lookup(out, innerQn)
        // Decorators stay on the impl function (callable-shim shape: decorators
        // wrap a function, not a class).
        assertEquals(listOf(deco), rewrittenInner.decorators)
        // Capturing child: bind site replaced by adapter ctor call. Adapter class
        // synthesized; its methods carry no decorators.
        val cls = adapter(out, "inner")
        assertEquals(emptyList(), cls.methods[0].decorators)
        assertEquals(emptyList(), cls.methods[1].decorators)
        val outerInsts = entryInsts(lookup(out, outerQn))
        // Capturing-child bind became a FlatCall to the adapter constructor.
        val ctor = outerInsts.filterIsInstance<FlatCall>().firstOrNull {
            (it.callee as? FlatGlobalRef)?.qualifiedName == cls.qualifiedName
        }
        assertNotNull(ctor)
    }

    /* ------------------------------------------------------------------ */
    /* 12. Transitive capture through closure-root METHOD                  */
    /* ------------------------------------------------------------------ */

    @Test
    fun `transitive capture through method-in-class-inside-function pass-through`() {
        // outer (TOP_LEVEL) → m (METHOD, parent=outer) → inner (NESTED_DEF,
        // parent=m) reads x from outer.
        //
        // Closure-root status now derives from "no parent". The method m has
        // a parent (outer), so it forwards x through cells just like any
        // other inner scope. The class-inside-function shape isn't producible
        // from real proto→Flat input today, but the analyzer/rewriter are
        // forward-compatible. No diagnostic, no `ClosureRewriteLimitation`.
        val outerQn = "m.outer"
        val mQn = "m.outer.C.m"
        val innerQn = "m.outer.C.m.inner"

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

        val out = FlatClosureTransformer.transform(module(listOf(outer, method, inner)))

        // outer: x cell allocated (descendant captures transitively).
        val outerInsts = entryInsts(lookup(out, outerQn))
        assertTrue(
            outerInsts.any {
                it is FlatCall &&
                    (it.callee as? FlatGlobalRef)?.qualifiedName == "builtins.${ClosureRuntime.CELL_CTOR_NAME}" &&
                    (it.target as? FlatLocal)?.name == cellName("x")
            },
            "outer should allocate \$cell\$x",
        )
        // inner: <self> param injected, $cell$x extracted from env.
        val rewrittenInner = lookup(out, innerQn)
        assertEquals(ClosureRuntime.SELF_PARAM_NAME, rewrittenInner.parameters[0].name)
        assertEquals(setOf("x"), rewrittenInner.closureVars)
        // m: parented, so forwards x. <self> injected; x in closureVars.
        val rewrittenM = lookup(out, mQn)
        assertEquals(ClosureRuntime.SELF_PARAM_NAME, rewrittenM.parameters[0].name)
        assertEquals(setOf("x"), rewrittenM.closureVars)
        // No diagnostic on m: rewrite succeeds.
        assertEquals(emptyList(), out.diagnostics.filter { it.functionName == mQn })
    }

    /* ------------------------------------------------------------------ */
    /* 13. closureVars surfaces on FlatFunctionIR for PIR conversion       */
    /* ------------------------------------------------------------------ */

    @Test
    fun `closureVars populated on rewritten FlatFunctionIR`() {
        val outerQn = "m.outer"
        val innerQn = "m.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            body = listOf(
                FlatBinOp(local("t"), local("a"), local("b"), FlatBinaryOperator.ADD),
                FlatReturn(local("t")),
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
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        assertEquals(setOf("a", "b"), lookup(out, innerQn).closureVars)
        assertEquals(emptySet(), lookup(out, outerQn).closureVars)
    }

    /* ------------------------------------------------------------------ */
    /* 14. Module init untouched when no closures                          */
    /* ------------------------------------------------------------------ */

    @Test
    fun `module init untouched when no closures`() {
        val out = FlatClosureTransformer.transform(module(functions = emptyList()))
        // moduleInit should be returned unchanged (same instance reasoning is
        // strict, just verify no <self> appears and no diagnostics added).
        assertEquals(emptyList(), out.moduleInit.parameters.map { it.name })
        assertEquals(emptySet(), out.moduleInit.closureVars)
        assertEquals(0, out.diagnostics.size)
    }
}
