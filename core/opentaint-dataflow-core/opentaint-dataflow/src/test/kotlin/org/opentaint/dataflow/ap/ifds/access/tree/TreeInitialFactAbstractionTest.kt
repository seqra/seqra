package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AccessPathBase.This
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonTypeName
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.api.common.cfg.ControlFlowGraph
import kotlin.test.Test
import kotlin.test.assertTrue

class TreeInitialFactAbstractionTest {
    private companion object {
        const val TYPE_A = "A"
        const val TYPE_B = "B"
        const val TYPE_C = "C"
        const val TYPE_D = "D"

        val FIELD_A_B = FieldAccessor(TYPE_A, "b", TYPE_B)
        val FIELD_B_C = FieldAccessor(TYPE_B, "c", TYPE_C)
        val FIELD_C_D = FieldAccessor(TYPE_C, "d", TYPE_D)
        val FIELD_B_E = FieldAccessor(TYPE_B, "e", TYPE_D)

        val MARK = TaintMarkAccessor("test-mark")
    }

    private val apManager: ApManager = TreeApManager(AnyAccessorUnrollStrategy.AnyAccessorDisabled)

    @Test
    fun `analyzed exclusion hit produces more precise abstraction`() {
        val analyzed = initialFact(This, FIELD_A_B).exclude(FIELD_B_C)

        abstraction.registerNewInitialFact(analyzed, FactTypeChecker.Dummy)

        val added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D)
        val produced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)

        val expected = initialFact(This, FIELD_A_B, FIELD_B_C)

        assertTrue(
            produced.any { (initial, final) -> initial == expected && final.equalTo(expected) },
            "Expected abstraction for a.b.c.* to be produced when c is excluded at a.b.*",
        )
    }

    @Test
    fun `non matching exclusion keeps broader abstraction`() {
        val analyzed = initialFact(This, FIELD_A_B).exclude(FIELD_B_E)

        abstraction.registerNewInitialFact(analyzed, FactTypeChecker.Dummy)

        val added = finalFact(This, FIELD_A_B, FIELD_B_C, FIELD_C_D)
        val produced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)

        assertTrue(
            produced.isEmpty(),
            "Expected no new abstraction because a.b.* is already analyzed and exclusion {e} does not match accessor c. Produced: $produced",
        )
    }

    @Test
    fun `always unroll next accessor without exclusion conflict produces no abstraction`() {
        val analyzed = initialFact(This, MARK)

        abstraction.registerNewInitialFact(analyzed, FactTypeChecker.Dummy)

        val added = finalFact(This, FIELD_A_B, ValueAccessor, MARK)
        val produced = abstraction.addAbstractedInitialFact(added, FactTypeChecker.Dummy)

        assertTrue(
            produced.isEmpty(),
            "Expected no new abstraction when always-unroll-next chain has no exclusion conflict. Produced: $produced",
        )
    }

    private fun initialFact(base: AccessPathBase, vararg accessors: Accessor): InitialFactAp {
        var fact = apManager.mostAbstractInitialAp(base)
        accessors.reversed().forEach {accessor ->
            fact = fact.prependAccessor(accessor)
        }
        return fact
    }

    private fun finalFact(base: AccessPathBase, vararg accessors: Accessor): FinalFactAp {
        var fact = apManager.createFinalAp(base, ExclusionSet.Empty)
        accessors.reversed().forEach {accessor ->
            fact = fact.prependAccessor(accessor)
        }
        return fact
    }

    private val dummyInst = object : CommonInst {
        override fun toString(): String = "dummy-inst"
        override val location: CommonInstLocation = object : CommonInstLocation {
            override val method: CommonMethod = object : CommonMethod {
                override val name: String = "dummy"
                override val parameters: List<CommonMethodParameter> = emptyList()
                override val returnType: CommonTypeName = object : CommonTypeName {
                    override val typeName: String = "void"
                }

                override fun flowGraph(): ControlFlowGraph<CommonInst> = object : ControlFlowGraph<CommonInst> {
                    override val instructions: List<CommonInst> = emptyList()
                    override val entries: List<CommonInst> = emptyList()
                    override val exits: List<CommonInst> = emptyList()
                    override fun successors(node: CommonInst): Set<CommonInst> = emptySet()
                    override fun predecessors(node: CommonInst): Set<CommonInst> = emptySet()
                }
            }
        }
    }

    private val abstraction = apManager.initialFactAbstraction(dummyInst)
}
