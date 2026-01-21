package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

inline fun <R> readPosition(
    ap: FinalFactAp,
    position: PositionAccess,
    onMismatch: (FinalFactAp, Accessor?) -> R,
    matchedNode: (FinalFactAp) -> R
): R = readPosition(ap, position, onMismatch, { readAccessor(it) }, matchedNode)

inline fun <R> readPosition(
    ap: InitialFactAp,
    position: PositionAccess,
    onMismatch: (InitialFactAp, Accessor?) -> R,
    matchedNode: (InitialFactAp) -> R
): R = readPosition(ap, position, onMismatch, { readAccessor(it) }, matchedNode)

inline fun <F: FactAp, R> readPosition(
    ap: F,
    position: PositionAccess,
    onMismatch: (F, Accessor?) -> R,
    readAccessor: F.(Accessor) -> F?,
    matchedNode: (F) -> R
): R {
    val accessors = mutableListOf<Accessor>()
    var currentPosition = position
    while (true) {
        when (currentPosition) {
            is PositionAccess.Complex -> {
                accessors.add(currentPosition.accessor)
                currentPosition = currentPosition.base
            }

            is PositionAccess.Simple -> {
                if (ap.base != currentPosition.base) {
                    return onMismatch(ap, null)
                }
                break
            }
        }
    }

    var result = ap
    while (accessors.isNotEmpty()) {
        val accessor = accessors.removeLast()

        if (!result.startsWithAccessor(accessor)) {
            return onMismatch(result, accessor)
        }

        result =  result.readAccessor(accessor) ?: error("Impossible")
    }

    return matchedNode(result)
}

fun readAnyPosition(
    ap: FinalFactAp,
    position: PositionAccess,
): PositionAccess? = readAnyPosition(ap, position, mutableListOf(), hashSetOf(), { readAccessor(it) })

fun readAnyPosition(
    ap: InitialFactAp,
    position: PositionAccess,
): PositionAccess? = readAnyPosition(ap, position, mutableListOf(), hashSetOf(), { readAccessor(it) })

fun <F : FactAp> readAnyPosition(
    ap: F,
    position: PositionAccess,
    accessors: MutableList<Accessor>,
    visited: MutableSet<F>,
    readAccessor: F.(Accessor) -> F?,
): PositionAccess? {
    readPosition(
        ap,
        position,
        readAccessor = readAccessor,
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
        val posAtFact = readAnyPosition(fact, position, accessors, visited, readAccessor)
        if (posAtFact != null) return posAtFact

        visited.remove(fact)
        accessors.removeLast()
    }

    return null
}
