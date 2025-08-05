package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

class AccessPathWithCycles(
    override val base: AccessPathBase,
    val access: AccessNode?,
    override val exclusions: ExclusionSet
): InitialFactAp {
    override fun exclude(accessor: Accessor): InitialFactAp =
        AccessPathWithCycles(base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp =
        AccessPathWithCycles(base, access, exclusions)

    override val size: Int
        get() = access?.size ?: 0

    override fun toString(): String = "$base${access ?: ""}.*/$exclusions"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccessPathWithCycles

        if (base != other.base) return false
        if (access != other.access) return false
        if (exclusions != other.exclusions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + access.hashCode()
        result = 31 * result + exclusions.hashCode()
        return result
    }

    data class AccessPathElement private constructor (
        private val node: AccessNode?,
        private val cyclePosition: Pair<Int, Int>? // Cycle number, position on cycle
    ) {
        val next: List<Pair<Accessor, AccessPathElement>>
            get() {
                if (node == null) {
                    return emptyList()
                }

                if (cyclePosition != null) {
                    val (cycleNumber, positionOnCycle) = cyclePosition
                    val curAccessor = node.cycles[cycleNumber][positionOnCycle]

                    val nextCyclePosition = if (positionOnCycle + 1 == node.cycles[cycleNumber].size) {
                        null
                    } else {
                        cycleNumber to (positionOnCycle + 1)
                    }

                    val nextPathElement = AccessPathElement(node, nextCyclePosition)
                    return listOf(curAccessor to nextPathElement)
                }

                return buildList {
                    add(node.accessor to AccessPathElement(node.next, null))
                    addAll(
                        node.cycles.mapIndexed { number, cycle ->
                            val nextPathElement = if (cycle.size == 1) {
                                AccessPathElement(node, null)
                            } else {
                                AccessPathElement(node, number to 1)
                            }

                            cycle[0] to nextPathElement
                        }
                    )
                }
            }

        companion object {
            fun fromAccessPath(accessPathNode: AccessNode?): AccessPathElement {
                return AccessPathElement(accessPathNode, null)
            }
        }
    }

    class AccessNode private constructor(
        val accessor: Accessor,
        val next: AccessNode?,
        val cycles: List<Cycle>
    ): Iterable<Pair<Accessor, List<Cycle>>> {
        private val hash: Int
        val size: Int

        init {
            var hash = accessor.hashCode() * 31 + cycles.hashCode()
            if (next != null) hash += 63 * next.hash
            this.hash = hash
        }

        init {
            var size = 1
            if (next != null) size += next.size
            this.size = size
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessNode) return false

            if (hash != other.hash) return false
            if (accessor != other.accessor) return false

            return next == other.next
        }

        override fun iterator(): Iterator<Pair<Accessor, List<Cycle>>> = object : Iterator<Pair<Accessor, List<Cycle>>> {
            private var node: AccessNode? = this@AccessNode

            override fun hasNext(): Boolean = node != null

            override fun next(): Pair<Accessor, List<Cycle>> {
                val node = this.node ?: error("Iterator invariant")
                val accessor = node.accessor
                val cycles = node.cycles
                this.node = node.next
                return accessor to cycles
            }
        }

        override fun toString(): String = joinToString("") { node ->
            node.second.joinToString("") { cycle ->
                "{${cycle.joinToString("") { it.toSuffix() }}}"
            } + node.first.toSuffix()
        }

        class Builder {
            private val nodes: MutableList<Pair<Accessor, List<Cycle>>> = mutableListOf()

            fun build(): AccessNode? {
                return nodes.foldRight<_, AccessNode?>(null) { (accessor, cycles), nextNode ->
                    AccessNode(accessor, nextNode, cycles)
                }
            }

            fun append(accessor: Accessor, cycles: List<Cycle>) {
                nodes.add(accessor to cycles)
            }

            fun removeLast() {
                nodes.removeLast()
            }
        }
    }
}