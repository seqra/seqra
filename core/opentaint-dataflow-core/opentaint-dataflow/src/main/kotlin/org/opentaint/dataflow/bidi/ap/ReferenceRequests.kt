package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.bidi.store.CallerRef

/** Pure scalar-oracle vocabulary. Runtime events, services and AP outputs do not accept this type. */
sealed interface ReferenceRequestKind {
    data class Mark(val mark: TaintMark) : ReferenceRequestKind
    data class Position(val path: PathNode) : ReferenceRequestKind
}

/** One result of the historical scalar join, used by reference closure and protocol tests only. */
sealed interface ReferenceRequestAction {
    class Answer(val initial: InitialAp) : ReferenceRequestAction
    class Climb(val premise: InitialAp, val request: ReferenceRequestKind) : ReferenceRequestAction
    data object None : ReferenceRequestAction
}

/** The oracle caller first enforces geometric overlap. This function does not send or admit work. */
fun referenceRequestAction(m: ApManager, i: InitialAp, kind: ReferenceRequestKind,
                           a: Pattern, caller: CallerRef): ReferenceRequestAction {
    m.cancellation.checkpoint()
    check(i.tail == Tail.STAR && i.exclusion == ExclusionSet.Empty && i.mark == MarkSlot.STAR) {
        "A reference request needs a policy initial or position answer"
    }
    return when (kind) {
        is ReferenceRequestKind.Mark -> when (val mark = a.fact.mark) {
            is MarkSlot.Concrete -> if (mark.mark == kind.mark)
                ReferenceRequestAction.Answer(m.initial(answer(i.toPattern(), a, kind.mark)))
            else ReferenceRequestAction.None
            is MarkSlot.Star -> if (kind.mark in mark.excluded) ReferenceRequestAction.None
            else ReferenceRequestAction.Climb(caller.premise as InitialAp, kind)
        }
        is ReferenceRequestKind.Position -> when {
            a.fact.path.startsWith(kind.path.toList()) -> ReferenceRequestAction.Answer(
                m.initial(STATIC, kind.path, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR))
            (caller.premise as? InitialAp)?.base == STATIC ->
                ReferenceRequestAction.Climb(caller.premise as InitialAp, kind)
            else -> ReferenceRequestAction.None
        }
    }
}
