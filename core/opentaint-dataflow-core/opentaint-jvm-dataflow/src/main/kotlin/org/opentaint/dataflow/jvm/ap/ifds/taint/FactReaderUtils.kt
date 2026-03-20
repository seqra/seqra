package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.ReadableAccessorList
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.analysis.apAccessor
import org.opentaint.ir.api.jvm.cfg.JIRInst

inline fun <R> readPosition(
    ap: FinalFactAp,
    position: PositionAccess,
    onMismatch: (FinalFactAp, Accessor?) -> R,
    matchedNode: (FinalFactAp) -> R
): R = readPositionUtil(ap, ap.base, position, onMismatch, matchedNode)

inline fun <R> readPosition(
    ap: InitialFactAp,
    position: PositionAccess,
    onMismatch: (InitialFactAp, Accessor?) -> R,
    matchedNode: (InitialFactAp) -> R
): R = readPositionUtil(ap, ap.base, position, onMismatch, matchedNode)

inline fun <F, R> readPositionUtil(
    ap: F,
    apBase: AccessPathBase,
    position: PositionAccess,
    onMismatch: (F, Accessor?) -> R,
    matchedNode: (F) -> R
): R where F: ReadableAccessorList<F> {
    val accessors = mutableListOf<Accessor>()
    var currentPosition = position
    while (true) {
        when (currentPosition) {
            is PositionAccess.Complex -> {
                accessors.add(currentPosition.accessor)
                currentPosition = currentPosition.base
            }

            is PositionAccess.Simple -> {
                if (apBase != currentPosition.base) {
                    return onMismatch(ap, null)
                }
                break
            }
        }
    }

    var result = ap
    while (accessors.isNotEmpty()) {
        val accessor = accessors.removeLast()

        if (accessor is AnyAccessor) {
            accessors.add(accessor)
            return readWithAnyAccessorSplit(result, accessors, matchedNode, onMismatch)
        }

        if (!result.startsWithAccessor(accessor)) {
            return onMismatch(result, accessor)
        }

        result = result.readAccessor(accessor) ?: error("Impossible")
    }

    return matchedNode(result)
}

inline fun <F, R> readWithAnyAccessorSplit(
    ap: F,
    accessors: MutableList<Accessor>,
    matchedNode: (F) -> R,
    onMismatch: (F, Accessor?) -> R
): R where F : ReadableAccessorList<F> {
    val mismatchedNodes = mutableListOf<Pair<F, Accessor>>()
    val visitedNodes = hashSetOf<F>()
    val resultNode = readPositionWithAnyAccessorSplit(ap, accessors, mismatchedNodes, visitedNodes)
    if (resultNode !== null) {
        return matchedNode(resultNode)
    }

    val firstMismatch = mismatchedNodes.firstOrNull()
        ?: error("Impossible")

    return onMismatch(firstMismatch.first, firstMismatch.second)
}

fun <F> readPositionWithAnyAccessorSplit(
    result: F,
    accessors: MutableList<Accessor>,
    mismatched: MutableList<Pair<F, Accessor>>,
    visited: MutableSet<F>,
): F? where F : ReadableAccessorList<F> {
    if (accessors.isEmpty()) {
        return result
    }

    val accessor = accessors.removeLast()
    if (accessor is AnyAccessor) {
        val startAccessors = result.getStartAccessors()
        for (nextAccessor in startAccessors) {
            val nextNode = result.readAccessor(nextAccessor)
                ?: error("Impossible")

            if (!visited.add(nextNode)) continue

            val nextSimpleResult = readPositionWithAnyAccessorSplit(
                nextNode, accessors.toMutableList(), mismatched, visited
            )

            if (nextSimpleResult !== null) {
                return nextSimpleResult
            }

            val accessorsWithAny = accessors.toMutableList()
            accessorsWithAny.add(AnyAccessor)

            val nextAnyResult = readPositionWithAnyAccessorSplit(
                nextNode, accessorsWithAny, mismatched, visited
            )

            if (nextAnyResult !== null) {
                return nextAnyResult
            }
        }

        return null
    }

    if (!result.startsWithAccessor(accessor)) {
        mismatched.add(result to accessor)
        return null
    }

    val next = result.readAccessor(accessor)
        ?: error("Impossible")

    return readPositionWithAnyAccessorSplit(next, accessors, mismatched, visited)
}

fun readAnyPosition(
    ap: FinalFactAp,
    position: PositionAccess,
): PositionAccess? = readAnyPositionUtil(ap, position, mutableListOf(), hashSetOf())

fun readAnyPosition(
    ap: InitialFactAp,
    position: PositionAccess,
): PositionAccess? = readAnyPositionUtil(ap, position, mutableListOf(), hashSetOf())

fun <F> readAnyPositionUtil(
    ap: F,
    position: PositionAccess,
    accessors: MutableList<Accessor>,
    visited: MutableSet<F>,
): PositionAccess? where F: FactAp, F: ReadableAccessorList<F> {
    readPositionUtil(
        ap,
        ap.base,
        position,
        onMismatch = { _, _ -> },
        matchedNode = { _ ->
            return position.withPrefix(accessors)
        }
    )

    val allAccessors = ap.getStartAccessors()
    val nextFacts = allAccessors.mapNotNull { accessor ->
        ap.readAccessor(accessor)?.let { accessor to it }
    }

    for ((accessor, fact) in nextFacts) {
        if (!visited.add(fact)) continue

        accessors.add(accessor)
        val posAtFact = readAnyPositionUtil(fact, position, accessors, visited)
        if (posAtFact != null) return posAtFact

        visited.remove(fact)
        accessors.removeLast()
    }

    return null
}

fun JIRLocalAliasAnalysis.forEachAliasAfterStatement(
    statement: JIRInst,
    reader: FinalFactReader,
    newBase: AccessPathBase.LocalVar,
    body: (FinalFactAp, AliasApInfo) -> Unit
) {
    val aliases = findAliasAfterStatement(newBase, statement) ?: return
    aliases.filterIsInstance<AliasApInfo>()
        .filterNot { alias -> alias.base is AccessPathBase.Constant }
        .forEach { alias -> applyAlias(reader, newBase, alias, body) }
}

private fun applyAlias(
    reader: FinalFactReader,
    newBase: AccessPathBase.LocalVar,
    alias: AliasApInfo,
    body: (FinalFactAp, AliasApInfo) -> Unit
) {
    if (!reader.containsPosition(alias.toAp())) {
        return
    }

    val result = alias.accessors.fold(reader.factAp.rebase(newBase)) { f, accessor ->
        val apAccessor = accessor.apAccessor()
        f.readAccessor(apAccessor) ?: error("Unexpected null accessor read")
    }

    body(result, alias)
}

fun AliasApInfo.toAp(): PositionAccess =
    accessors.fold(PositionAccess.Simple(base)) { acc: PositionAccess, accessor ->
        PositionAccess.Complex(acc, accessor.apAccessor())
    }
