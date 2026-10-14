package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker

enum class CleanReach { EXACT, BELOW, AT_AND_BELOW }

/** A cleaner targets one program value, optionally for one mark. */
class Cleaner(val base: AccessPathBase, val path: PathNode?, val reach: CleanReach, val mark: TaintMark?) {
    init { check(base != AccessPathBase.Zero) { "S11: a cleaner cannot target zero" } }
    @JvmField internal val pathArray: IntArray = path?.toIntArray() ?: EMPTY_PATH
}

/** The mark policy of interpreter.md §5.1 (`markPolicyKeeps`): it reads a concrete mark on the value of the base itself
 *  (`elements = 0`) or on the value at `[e]^k` below the base (`elements = k`: the k-th element type), as today. It is NOT
 *  a may-predicate: it reads the mark, not the path, and it can drop a real flow (outside ap.md S5; gap G6). Part II
 *  builds it from the static type t: `MarkPolicy { mark, k -> the type at level k is not primitive or boxed, or
 *  isPrimitiveTracking(mark) }`; null if no level of t is primitive or boxed. */
fun interface MarkPolicy { fun keeps(mark: TaintMark, elements: Int): Boolean }

/** ap.md §4.8: the primitive `filter(b, may)`; `may` is prefix-closed (S5). The base is the key of
 *  StatementSummary.typeFilters (and resultFilters, Part II §23.2). Part II builds `may` with JIRFactTypeChecker (REUSE of
 *  its FactApFilter). `markPolicy` is the policy of the same static type and of its element types, or null (DD9). */
class TypeFilter(val may: FactTypeChecker.FactApFilter, val markPolicy: MarkPolicy? = null) {
    /** interpreter.md §5.1: two filters on one base are a conjunction. */
    fun and(o: TypeFilter): TypeFilter = TypeFilter(AndFilter(may, o.may), when {
        markPolicy == null -> o.markPolicy
        o.markPolicy == null -> markPolicy
        else -> MarkPolicy { t, k -> markPolicy.keeps(t, k) && o.markPolicy.keeps(t, k) }
    })

    private class AndFilter(val a: FactTypeChecker.FactApFilter, val b: FactTypeChecker.FactApFilter) : FactTypeChecker.FactApFilter {
        override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
            val x = a.check(accessor)
            val y = b.check(accessor)
            return when {
                x === FactTypeChecker.FilterResult.Reject || y === FactTypeChecker.FilterResult.Reject -> FactTypeChecker.FilterResult.Reject
                x === FactTypeChecker.FilterResult.Accept -> y                                   // Accept and r: r
                y === FactTypeChecker.FilterResult.Accept -> x
                else -> FactTypeChecker.FilterResult.FilterNext(                               // both FilterNext: the conjunction below
                    AndFilter((x as FactTypeChecker.FilterResult.FilterNext).filter, (y as FactTypeChecker.FilterResult.FilterNext).filter))
            }
        }
    }
}


class ConjunctiveEdge(val literals: List<Pattern>, val target: PathFact) {
    init {
        check(literals.size >= 2)
        check(literals.all { it.fact.mark is MarkSlot.Concrete && (it.fact.tail == Tail.EXACT || it.fact.tail == Tail.ANY) })
        check(target.mark is MarkSlot.Concrete && (target.tail == Tail.EXACT || target.tail == Tail.ANY_TAINT))   // W7, I14
        check(target.base != AccessPathBase.Zero)                       // so targetTree(target, layer) is a TaintTree
    }
}
