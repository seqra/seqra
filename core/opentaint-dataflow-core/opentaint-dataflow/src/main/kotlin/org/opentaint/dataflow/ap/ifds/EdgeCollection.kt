package org.opentaint.dataflow.ap.ifds

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.ir.api.common.cfg.CommonInst

object EdgeCollection {
    class EdgeList(
        private val apManager: ApManager,
        private val methodEntryPoint: MethodEntryPoint
    ) {
        private val kinds = IntArrayList()

        private val normalEdges = arrayListOf<Edge>()
        private var z2f: Z2F? = null
        private var f2f: F2F? = null
        private var ndF2F: NDF2F? = null

        val isEmpty: Boolean get() = kinds.isEmpty
        val size: Int get() = kinds.size

        fun toList(): List<Edge> {
            val result = ArrayList<Edge>(kinds.size)
            val indices = IntArray(KIND_COUNT)
            for (i in 0 until kinds.size) {
                val kind = kinds.getInt(i)
                val storage = when (kind) {
                    KIND_NORMAL -> {
                        result.add(normalEdges[indices[KIND_NORMAL]++])
                        continue
                    }
                    KIND_ZERO_TO_FACT -> z2f
                    KIND_FACT_TO_FACT -> f2f
                    KIND_ND_FACT_TO_FACT -> ndF2F
                    else -> error("Impossible edge kind")
                }

                val edge = storage!!.get(indices[kind]++, methodEntryPoint)
                result.add(edge)
            }
            return result
        }

        fun add(edge: Edge) {
            if (!apManager.listEdgeCompressionRequired(edge)) {
                kinds.add(KIND_NORMAL)
                normalEdges.add(edge)
                return
            }

            when (edge) {
                is Edge.ZeroToZero -> error("Z2Z edge compression is impossible")
                is Edge.ZeroToFact -> {
                    kinds.add(KIND_ZERO_TO_FACT)
                    val s = z2f ?: Z2F(apManager).also { z2f = it }
                    s.add(edge)
                }
                is Edge.FactToFact -> {
                    kinds.add(KIND_FACT_TO_FACT)
                    val s = f2f ?: F2F(apManager).also { f2f = it }
                    s.add(edge)
                }
                is Edge.NDFactToFact -> {
                    kinds.add(KIND_ND_FACT_TO_FACT)
                    val s = ndF2F ?: NDF2F(apManager).also { ndF2F = it }
                    s.add(edge)
                }
            }
        }

        fun removeLast(): Edge {
            val storage = when (kinds.removeInt(kinds.size - 1)) {
                KIND_NORMAL -> return normalEdges.removeLast()
                KIND_ZERO_TO_FACT -> z2f
                KIND_FACT_TO_FACT -> f2f
                KIND_ND_FACT_TO_FACT -> ndF2F
                else -> error("Impossible edge kind")
            }

            return storage!!.removeLast(methodEntryPoint)
        }

        private interface Storage {
            fun get(idx: Int, methodEntryPoint: MethodEntryPoint): Edge
            fun removeLast(methodEntryPoint: MethodEntryPoint): Edge
        }

        private class Z2F(manager: ApManager): Storage {
            private val statement = arrayListOf<CommonInst>()
            private val facts = manager.finalFactList()

            fun add(edge: Edge.ZeroToFact) {
                statement.add(edge.statement)
                facts.add(edge.factAp)
            }

            override fun get(idx: Int, methodEntryPoint: MethodEntryPoint) =
                Edge.ZeroToFact(methodEntryPoint, statement[idx], facts.get(idx))

            override fun removeLast(methodEntryPoint: MethodEntryPoint) =
                Edge.ZeroToFact(methodEntryPoint, statement.removeLast(), facts.removeLast())
        }

        private class F2F(manager: ApManager): Storage {
            private val initial = arrayListOf<InitialFactAp>()
            private val statement = arrayListOf<CommonInst>()
            private val finalFact = manager.finalFactList()

            fun add(edge: Edge.FactToFact) {
                initial.add(edge.initialFactAp)
                statement.add(edge.statement)
                finalFact.add(edge.factAp)
            }

            override fun get(idx: Int, methodEntryPoint: MethodEntryPoint) =
                Edge.FactToFact(methodEntryPoint, initial[idx], statement[idx], finalFact.get(idx))

            override fun removeLast(methodEntryPoint: MethodEntryPoint) =
                Edge.FactToFact(methodEntryPoint, initial.removeLast(), statement.removeLast(), finalFact.removeLast())

        }

        private class NDF2F(manager: ApManager): Storage {
            private val initial = arrayListOf<Set<InitialFactAp>>()
            private val statement = arrayListOf<CommonInst>()
            private val finalFact = manager.finalFactList()

            fun add(edge: Edge.NDFactToFact) {
                initial.add(edge.initialFacts)
                statement.add(edge.statement)
                finalFact.add(edge.factAp)
            }

            override fun get(idx: Int, methodEntryPoint: MethodEntryPoint) =
                Edge.NDFactToFact(methodEntryPoint, initial[idx], statement[idx], finalFact.get(idx))

            override fun removeLast(methodEntryPoint: MethodEntryPoint) =
                Edge.NDFactToFact(methodEntryPoint, initial.removeLast(), statement.removeLast(), finalFact.removeLast())
        }

        private companion object {
            const val KIND_NORMAL = 0
            const val KIND_ZERO_TO_FACT = 1
            const val KIND_FACT_TO_FACT = 2
            const val KIND_ND_FACT_TO_FACT = 3
            const val KIND_COUNT = 4
        }
    }

    class EdgeSet {
        private val edgeSet = ObjectOpenHashSet<Edge>()

        fun add(edge: Edge): Boolean = edgeSet.add(edge)
    }
}
