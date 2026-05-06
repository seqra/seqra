package org.opentaint.ir.test.python.transforms.closure

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.python.PIRDiagnostic
import org.opentaint.ir.impl.python.flat.FlatAnyType
import org.opentaint.ir.impl.python.flat.FlatArgKind
import org.opentaint.ir.impl.python.flat.FlatAssign
import org.opentaint.ir.impl.python.flat.FlatBindFunction
import org.opentaint.ir.impl.python.flat.FlatBlock
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatDecorator
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
import org.opentaint.ir.impl.python.flat.FlatStoreAttr
import org.opentaint.ir.impl.python.transforms.closure.ClosureRuntime
import org.opentaint.ir.impl.python.transforms.closure.FlatClosureTransformer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the callable-shim closure shape: capturing nested defs and
 * lambdas emit a synthetic adapter `FlatClass` plus a renamed impl function.
 * Bind sites for capturing children become constructor calls instead of
 * `FlatBindFunction`.
 *
 * See `.agents/callable-shim/plan.md` for the full design.
 */
@Tag("tier2")
class CallableShimTest {

    private val moduleName = "m"

    private fun param(
        name: String,
        kind: FlatParamKind = FlatParamKind.POSITIONAL_OR_KEYWORD,
        hasDefault: Boolean = false,
        defaultValue: org.opentaint.ir.impl.python.flat.FlatConst? = null,
    ) = FlatParameter(
        name = name,
        type = FlatAnyType,
        kind = kind,
        hasDefault = hasDefault,
        defaultValue = defaultValue,
    )

    private fun fn(
        name: String,
        qualifiedName: String,
        parent: String?,
        kind: FlatFunctionKind,
        params: List<FlatParameter> = emptyList(),
        body: List<FlatInst> = listOf(FlatReturn(null)),
        decorators: List<FlatDecorator> = emptyList(),
    ): FlatFunctionIR = FlatFunctionIR(
        name = name,
        qualifiedName = qualifiedName,
        parentQualifiedName = parent,
        kind = kind,
        cfg = FlatCFG(
            blocks = listOf(FlatBlock(0, body, emptyList())),
            entryBlock = 0,
            exitBlocks = listOf(0),
        ),
        parameters = params,
        returnType = FlatAnyType,
        isAsync = false,
        isGenerator = false,
        decorators = decorators,
    )

    private fun moduleInit() = fn(
        name = "<module>",
        qualifiedName = "$moduleName.<module>",
        parent = null,
        kind = FlatFunctionKind.MODULE_INIT,
    )

    private fun module(functions: List<FlatFunctionIR>): FlatModuleIR = FlatModuleIR(
        moduleName = moduleName,
        path = "$moduleName.py",
        functions = functions,
        moduleInit = moduleInit(),
        classes = emptyList(),
        fields = emptyList(),
        imports = emptyList(),
        diagnostics = emptyList<PIRDiagnostic>(),
    )

    private fun adapterFor(out: FlatModuleIR, baseName: String): FlatClass {
        val expected = "<closure_$baseName>"
        return out.classes.firstOrNull { it.name == expected }
            ?: error("Adapter $expected not found; classes=${out.classes.map { it.name }}")
    }

    private fun implFor(out: FlatModuleIR, baseName: String): FlatFunctionIR {
        val expected = "<closure_${baseName}_impl>"
        return out.functions.firstOrNull { it.name == expected }
            ?: error("Impl $expected not found")
    }

    /* ------------------------------------------------------------------ */

    @Test
    fun `capturing nested def emits adapter class + renamed impl`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("p")),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )

        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))

        // 1. Adapter class is in module.classes.
        val cls = adapterFor(out, "inner")
        // 2. Adapter class name + qn use angle brackets.
        assertTrue(cls.name.contains('<') && cls.name.contains('>'))
        assertTrue(cls.qualifiedName.contains('<') && cls.qualifiedName.contains('>'))
        assertEquals("$moduleName.${cls.name}", cls.qualifiedName)
        // 3. Two methods: __init__, __call__.
        assertEquals(listOf("__init__", "__call__"), cls.methods.map { it.name })
        // 4. Impl is renamed to <closure_inner_impl> and stays in module.functions.
        val impl = implFor(out, "inner")
        // 5. Impl <self> is at index 0.
        assertEquals(ClosureRuntime.SELF_PARAM_NAME, impl.parameters[0].name)
        assertEquals("p", impl.parameters[1].name)
    }

    @Test
    fun `init method stores _closure_env_ on self`() {
        val out = simpleCapturingModule()
        val cls = adapterFor(out, "inner")
        val initMethod = cls.methods[0]
        assertEquals(listOf("self", ClosureRuntime.CLOSURE_ATTR_NAME), initMethod.parameters.map { it.name })
        val store = initMethod.cfg.blocks.single().instructions.filterIsInstance<FlatStoreAttr>().single()
        assertEquals("self", (store.obj as FlatLocal).name)
        assertEquals(ClosureRuntime.CLOSURE_ATTR_NAME, store.attribute)
        assertEquals(ClosureRuntime.CLOSURE_ATTR_NAME, (store.value as FlatLocal).name)
    }

    @Test
    fun `call method forwards self + user params positionally to impl`() {
        val out = simpleCapturingModule()
        val cls = adapterFor(out, "inner")
        val callMethod = cls.methods[1]
        assertEquals(listOf("self", "p"), callMethod.parameters.map { it.name })
        val implCall = callMethod.cfg.blocks.single().instructions.filterIsInstance<FlatCall>().single()
        val callee = implCall.callee as FlatGlobalRef
        assertEquals("<closure_inner_impl>", callee.name)
        assertEquals(2, implCall.args.size)
        assertEquals("self", (implCall.args[0].value as FlatLocal).name)
        assertEquals(FlatArgKind.POSITIONAL, implCall.args[0].kind)
        assertEquals("p", (implCall.args[1].value as FlatLocal).name)
        assertEquals(FlatArgKind.POSITIONAL, implCall.args[1].kind)
    }

    @Test
    fun `non-capturing nested def emits no adapter class`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("p")),
            body = listOf(FlatReturn(FlatLocal("p"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        // No adapter class.
        assertTrue(out.classes.none { it.name.startsWith("<closure_") })
        // Impl keeps its original qn.
        assertNotNull(out.functions.firstOrNull { it.qualifiedName == innerQn })
        // Bind site is preserved as FlatBindFunction (no constructor rewrite).
        val outerInsts = out.functions.first { it.qualifiedName == outerQn }.cfg.blocks.single().instructions
        assertTrue(outerInsts.any { it is FlatBindFunction })
    }

    @Test
    fun `capturing lambda gets the same shape as nested def`() {
        val outerQn = "$moduleName.outer"
        val lambdaQn = "$moduleName.outer.<lambda>\$0"
        val lam = fn(
            name = "<lambda>\$0",
            qualifiedName = lambdaQn,
            parent = outerQn,
            kind = FlatFunctionKind.LAMBDA,
            params = listOf(param("a")),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("fn"), FlatGlobalRef("<lambda>\$0", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, lam)))
        val cls = adapterFor(out, "<lambda>\$0")
        assertEquals(2, cls.methods.size)
        val impl = implFor(out, "<lambda>\$0")
        assertEquals(ClosureRuntime.SELF_PARAM_NAME, impl.parameters[0].name)
    }

    @Test
    fun `bind site for capturing child emits FlatCall to adapter ctor not FlatBindFunction`() {
        val out = simpleCapturingModule()
        val outerQn = "$moduleName.outer"
        val outer = out.functions.first { it.qualifiedName == outerQn }
        val insts = outer.cfg.blocks.first { it.label == outer.cfg.entryBlock }.instructions
        // No FlatBindFunction for the capturing child.
        assertFalse(insts.any { it is FlatBindFunction })
        // Find the adapter ctor call.
        val cls = adapterFor(out, "inner")
        val ctor = insts.filterIsInstance<FlatCall>().firstOrNull {
            (it.callee as? FlatGlobalRef)?.name == cls.name
        }
        assertNotNull(ctor)
        assertEquals("inner", (ctor!!.target as FlatLocal).name)
        // Ctor receives exactly one positional arg: the env dict.
        assertEquals(1, ctor.args.size)
        assertEquals(FlatArgKind.POSITIONAL, ctor.args[0].kind)
    }

    @Test
    fun `bind site for non-capturing child still emits FlatBindFunction`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("p")),
            body = listOf(FlatReturn(FlatLocal("p"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val outerOut = out.functions.first { it.qualifiedName == outerQn }
        val insts = outerOut.cfg.blocks.single().instructions
        assertTrue(insts.any { it is FlatBindFunction })
    }

    @Test
    fun `default values are mirrored on call method`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(
                param("p", hasDefault = true, defaultValue = FlatIntConst(7)),
            ),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val cls = adapterFor(out, "inner")
        val callP = cls.methods[1].parameters[1]
        assertEquals("p", callP.name)
        assertTrue(callP.hasDefault)
        assertEquals(FlatIntConst(7), callP.defaultValue)
    }

    @Test
    fun `var positional parameter forwarded with STAR kind`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("args", kind = FlatParamKind.VAR_POSITIONAL)),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val cls = adapterFor(out, "inner")
        val callMethod = cls.methods[1]
        // Adapter __call__ should declare the same VAR_POSITIONAL param.
        val argsParam = callMethod.parameters[1]
        assertEquals("args", argsParam.name)
        assertEquals(FlatParamKind.VAR_POSITIONAL, argsParam.kind)
        // Forward arg uses STAR kind.
        val implCall = callMethod.cfg.blocks.single().instructions.filterIsInstance<FlatCall>().single()
        val starArg = implCall.args[1]
        assertEquals(FlatArgKind.STAR, starArg.kind)
        assertEquals("args", (starArg.value as FlatLocal).name)
    }

    @Test
    fun `var keyword parameter forwarded with DOUBLE_STAR kind`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("kwargs", kind = FlatParamKind.VAR_KEYWORD)),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val cls = adapterFor(out, "inner")
        val callMethod = cls.methods[1]
        val kwParam = callMethod.parameters[1]
        assertEquals("kwargs", kwParam.name)
        assertEquals(FlatParamKind.VAR_KEYWORD, kwParam.kind)
        val implCall = callMethod.cfg.blocks.single().instructions.filterIsInstance<FlatCall>().single()
        val ddArg = implCall.args[1]
        assertEquals(FlatArgKind.DOUBLE_STAR, ddArg.kind)
        assertEquals("kwargs", (ddArg.value as FlatLocal).name)
    }

    @Test
    fun `keyword-only parameter forwarded with KEYWORD kind`() {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("k", kind = FlatParamKind.KEYWORD_ONLY)),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, inner)))
        val cls = adapterFor(out, "inner")
        val callMethod = cls.methods[1]
        val kParam = callMethod.parameters[1]
        assertEquals("k", kParam.name)
        assertEquals(FlatParamKind.KEYWORD_ONLY, kParam.kind)
        val implCall = callMethod.cfg.blocks.single().instructions.filterIsInstance<FlatCall>().single()
        val kArg = implCall.args[1]
        assertEquals(FlatArgKind.KEYWORD, kArg.kind)
        assertEquals("k", kArg.keyword)
        assertEquals("k", (kArg.value as FlatLocal).name)
    }

    @Test
    fun `cell-managed bind target wraps adapter ctor through temp then store`() {
        // Sibling pattern: a, b, where b captures a (so a is cell-managed in
        // outer), and b is also captured by another sibling c (so b is
        // cell-managed too). Then binding b lands in a cell.
        val outerQn = "$moduleName.outer"
        val aQn = "$moduleName.outer.a"
        val bQn = "$moduleName.outer.b"
        val cQn = "$moduleName.outer.c"
        val a = fn(
            name = "a",
            qualifiedName = aQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = emptyList(),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val b = fn(
            name = "b",
            qualifiedName = bQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = emptyList(),
            body = listOf(FlatReturn(FlatLocal("a"))),
        )
        val c = fn(
            name = "c",
            qualifiedName = cQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = emptyList(),
            body = listOf(FlatReturn(FlatLocal("b"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("a"), FlatGlobalRef("a", moduleName)),
                FlatBindFunction(FlatLocal("b"), FlatGlobalRef("b", moduleName)),
                FlatBindFunction(FlatLocal("c"), FlatGlobalRef("c", moduleName)),
                FlatReturn(null),
            ),
        )
        val out = FlatClosureTransformer.transform(module(listOf(outer, a, b, c)))
        val outerOut = out.functions.first { it.qualifiedName == outerQn }
        val insts = outerOut.cfg.blocks.first { it.label == outerOut.cfg.entryBlock }.instructions

        // For `b` (capturing & cell-managed): expect FlatBuildDict, FlatCall(adapter, into temp),
        // then FlatStoreAttr($cell$b, "value", temp).
        val clsB = adapterFor(out, "b")
        val ctorB = insts.filterIsInstance<FlatCall>().firstOrNull {
            (it.callee as? FlatGlobalRef)?.name == clsB.name
        }
        assertNotNull(ctorB)
        // ctor target is a fresh temp (not "b").
        val tmpName = (ctorB!!.target as FlatLocal).name
        assertTrue(tmpName.startsWith("\$t"), "ctor target should be a temp, got $tmpName")
        // After the ctor, expect a FlatStoreAttr to $cell$b's value attribute.
        val ctorIdx = insts.indexOf(ctorB)
        val store = insts[ctorIdx + 1] as FlatStoreAttr
        assertEquals("\$cell\$b", (store.obj as FlatLocal).name)
        assertEquals(ClosureRuntime.CELL_VALUE_ATTR, store.attribute)
        assertEquals(tmpName, (store.value as FlatLocal).name)
    }

    @Test
    fun `synthetic adapter class names contain angle brackets and cannot collide`() {
        val out = simpleCapturingModule()
        val cls = adapterFor(out, "inner")
        assertTrue(cls.name.startsWith("<closure_"))
        assertTrue(cls.name.endsWith(">"))
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private fun simpleCapturingModule(): FlatModuleIR {
        val outerQn = "$moduleName.outer"
        val innerQn = "$moduleName.outer.inner"
        val inner = fn(
            name = "inner",
            qualifiedName = innerQn,
            parent = outerQn,
            kind = FlatFunctionKind.NESTED_DEF,
            params = listOf(param("p")),
            body = listOf(FlatReturn(FlatLocal("x"))),
        )
        val outer = fn(
            name = "outer",
            qualifiedName = outerQn,
            parent = null,
            kind = FlatFunctionKind.TOP_LEVEL,
            body = listOf(
                FlatAssign(FlatLocal("x"), FlatIntConst(1)),
                FlatBindFunction(FlatLocal("inner"), FlatGlobalRef("inner", moduleName)),
                FlatReturn(null),
            ),
        )
        return FlatClosureTransformer.transform(module(listOf(outer, inner)))
    }

}
