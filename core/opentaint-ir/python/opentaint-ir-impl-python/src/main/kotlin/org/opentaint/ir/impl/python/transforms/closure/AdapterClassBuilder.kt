package org.opentaint.ir.impl.python.transforms.closure

import org.opentaint.ir.impl.python.flat.FlatAnyType
import org.opentaint.ir.impl.python.flat.FlatArgKind
import org.opentaint.ir.impl.python.flat.FlatBlock
import org.opentaint.ir.impl.python.flat.FlatCFG
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatCallArg
import org.opentaint.ir.impl.python.flat.FlatClass
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatFunctionKind
import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatLocal
import org.opentaint.ir.impl.python.flat.FlatParamKind
import org.opentaint.ir.impl.python.flat.FlatParameter
import org.opentaint.ir.impl.python.flat.FlatReturn
import org.opentaint.ir.impl.python.flat.FlatStoreAttr

/**
 * Build the user-visible adapter class for a capturing impl function.
 *
 * Shape:
 * ```
 * class <closure_$base>:
 *     def __init__(self, _closure_env_):
 *         self._closure_env_ = _closure_env_
 *     def __call__(self, ...impl-user-params...):
 *         return _impl(self, ...impl-user-params...)
 * ```
 *
 * The original impl's user-visible parameters (i.e. its parameters minus
 * the synthetic `<self>` we just prepended) are mirrored on `__call__`
 * exactly — same kinds, defaults, types. Forwarding uses arg kinds matched
 * to each parameter kind.
 *
 * Pure: depends only on [originalImpl] and [moduleName], not on any
 * per-function rewrite state. Adapter and impl qualified names are derived
 * from [originalImpl]'s already-unique `name` via [ClosureRuntime].
 */
internal fun buildAdapterClass(originalImpl: FlatFunctionIR, moduleName: String): FlatClass {
    val adapterQn = ClosureRuntime.adapterClassQn(moduleName, originalImpl.name)
    val implQn = ClosureRuntime.implFunctionQn(moduleName, originalImpl.name)
    val adapterName = adapterQn.substringAfterLast('.')

    return FlatClass(
        name = adapterName,
        qualifiedName = adapterQn,
        baseClasses = emptyList(),
        mro = emptyList(),
        methods = listOf(
            buildInitMethod(adapterQn),
            buildCallMethod(adapterQn, implQn, originalImpl),
        ),
        fields = emptyList(),
        nestedClasses = emptyList(),
        decorators = emptyList(),
        isAbstract = false,
        isDataclass = false,
        isEnum = false,
    )
}

private fun buildInitMethod(adapterQn: String): FlatFunctionIR {
    val selfLocal = FlatLocal("self")
    val envParamLocal = FlatLocal(ClosureRuntime.CLOSURE_ATTR_NAME)
    val cfg = FlatCFG(
        blocks = listOf(
            FlatBlock(
                label = 0,
                instructions = listOf(
                    FlatStoreAttr(
                        obj = selfLocal,
                        attribute = ClosureRuntime.CLOSURE_ATTR_NAME,
                        value = envParamLocal,
                    ),
                    FlatReturn(null),
                ),
                exceptionHandlers = emptyList(),
            ),
        ),
        entryBlock = 0,
        exitBlocks = listOf(0),
    )
    return FlatFunctionIR(
        name = "__init__",
        qualifiedName = "$adapterQn.__init__",
        parentQualifiedName = null,
        kind = FlatFunctionKind.METHOD,
        cfg = cfg,
        parameters = listOf(
            plainParameter("self"),
            plainParameter(ClosureRuntime.CLOSURE_ATTR_NAME),
        ),
        returnType = FlatAnyType,
        isAsync = false,
        isGenerator = false,
        decorators = emptyList(),
        closureVars = emptySet(),
        nonlocalNames = emptySet(),
        globalNames = emptySet(),
    )
}

/**
 * `__call__(self, p1, p2, …, *args, **kwargs)` — mirrors the impl's
 * user-visible parameters and forwards them positionally/keyword/star
 * to `_impl(self, …)`.
 */
private fun buildCallMethod(
    adapterQn: String,
    implQn: String,
    originalImpl: FlatFunctionIR,
): FlatFunctionIR {
    // Adapter `__call__` mirrors the impl's user-visible parameters
    // (= original impl parameters; the impl's synthetic <self> is added
    // by the prologue inside the impl itself).
    val implUserParams = originalImpl.parameters
    val callParams = listOf(plainParameter("self")) + implUserParams

    // Forward args: first positional is `self` (impl's <self>), then one
    // FlatCallArg per impl user-visible param, kind matched.
    val tmpReturn = FlatLocal("\$ret")
    val forwardArgs = listOf(FlatCallArg(FlatLocal("self"), FlatArgKind.POSITIONAL)) +
        implUserParams.map { p -> forwardArgFor(p) }

    val cfg = FlatCFG(
        blocks = listOf(
            FlatBlock(
                label = 0,
                instructions = listOf(
                    FlatCall(
                        target = tmpReturn,
                        callee = FlatGlobalRef(implQn),
                        args = forwardArgs,
                    ),
                    FlatReturn(tmpReturn),
                ),
                exceptionHandlers = emptyList(),
            ),
        ),
        entryBlock = 0,
        exitBlocks = listOf(0),
    )
    return FlatFunctionIR(
        name = "__call__",
        qualifiedName = "$adapterQn.__call__",
        parentQualifiedName = null,
        kind = FlatFunctionKind.METHOD,
        cfg = cfg,
        parameters = callParams,
        returnType = originalImpl.returnType,
        isAsync = originalImpl.isAsync,
        isGenerator = originalImpl.isGenerator,
        decorators = emptyList(),
        closureVars = emptySet(),
        nonlocalNames = emptySet(),
        globalNames = emptySet(),
    )
}

private fun forwardArgFor(p: FlatParameter): FlatCallArg {
    val v = FlatLocal(p.name)
    return when (p.kind) {
        FlatParamKind.POSITIONAL_OR_KEYWORD ->
            FlatCallArg(v, FlatArgKind.POSITIONAL)
        FlatParamKind.KEYWORD_ONLY ->
            FlatCallArg(v, FlatArgKind.KEYWORD, keyword = p.name)
        FlatParamKind.VAR_POSITIONAL ->
            FlatCallArg(v, FlatArgKind.STAR)
        FlatParamKind.VAR_KEYWORD ->
            FlatCallArg(v, FlatArgKind.DOUBLE_STAR)
    }
}

private fun plainParameter(name: String): FlatParameter = FlatParameter(
    name = name,
    type = FlatAnyType,
    kind = FlatParamKind.POSITIONAL_OR_KEYWORD,
    hasDefault = false,
    defaultValue = null,
)
