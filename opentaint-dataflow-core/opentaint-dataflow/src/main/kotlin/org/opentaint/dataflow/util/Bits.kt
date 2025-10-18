package org.opentaint.dataflow.util

import java.util.BitSet

fun BitSet.add(element: Int): Boolean {
    if (get(element)) return false
    set(element)
    return true
}

inline fun BitSet.forEach(action: (Int) -> Unit) {
    var node = nextSetBit(0)
    while (node >= 0) {
        action(node)
        node = nextSetBit(node + 1)
    }
}

fun BitSet.removeFirst(): Int {
    val node = nextSetBit(0)
    check(node >= 0) { "Set is empty" }
    clear(node)
    return node
}

fun BitSet.containsAll(other: BitSet): Boolean {
    val copy = other.clone() as BitSet
    copy.andNot(this@containsAll)
    return copy.isEmpty
}

fun BitSet.copy(): BitSet = clone() as BitSet
