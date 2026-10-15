package org.opentaint.dataflow.bidi.engine

import kotlinx.collections.immutable.PersistentMap
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ownedList
import org.opentaint.dataflow.bidi.store.RequestMarkSet

/** Query identity is independent of the requested marks and the caller which supplies an answer. */
enum class MarkQuestionRole { DYNAMIC, SOURCE, SINK, INPUT_GATE, CLEANER }

class MarkQuestionKey(initial: InitialAp, pattern: Pattern, val role: MarkQuestionRole) {
    val initial: InitialAp = initial
    val pattern: Pattern = pattern.copy(fact = pattern.fact.copy(
        path = ownedList(pattern.fact.path), mark = MarkSlot.STAR))
    private val hash = 31 * (31 * initial.hashCode() + this.pattern.hashCode()) + role.hashCode()

    init {
        require(initial.tail == Tail.STAR && initial.mark == MarkSlot.STAR &&
            initial.exclusion == ExclusionSet.Empty) { "A mark question needs an abstract original initial" }
        require(this.pattern.fact.base == initial.base &&
            this.pattern.fact.path.startsWith(initial.path?.toList().orEmpty())) {
            "A query must extend its original initial slot on the same base"
        }
    }

    override fun equals(other: Any?): Boolean = this === other || other is MarkQuestionKey &&
        initial === other.initial && pattern == other.pattern && role == other.role
    override fun hashCode(): Int = hash
    override fun toString(): String = "$role:${initial.id}:$pattern"

    companion object {
        fun dynamic(initial: InitialAp): MarkQuestionKey =
            MarkQuestionKey(initial, initial.toPattern(), MarkQuestionRole.DYNAMIC)
    }
}

/** Immutable, owned primitive membership. This delta retains no supplier payload or compiled form. */
data class QuestionDelta(val key: MarkQuestionKey, val marks: RequestMarkSet)

/** One current root is published per callee; subscribers borrow it without copying the relation. */
typealias QuestionView = PersistentMap<MarkQuestionKey, RequestMarkSet>
