package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.cfg.*
import org.opentaint.ir.api.ext.cfg.applyAndGet
import org.opentaint.ir.impl.cfg.util.ExprMapper
import org.opentaint.ir.impl.cfg.util.InstructionFilter

/**
 * a class that simplifies the instruction list after construction
 * a simplification process is required, because the construction process
 * naturally introduces some redundancy into the code (mainly because of
 * the frames merging)
 */
internal class Simplifier {

    fun simplify(jIRClasspath: JIRClasspath, instList: JIRInstListImpl<JIRRawInst>): JIRInstListImpl<JIRRawInst> {
        // clear the assignments that are repeated inside single basic block
        var instructionList = cleanRepeatedAssignments(instList)

        do {
            // delete the assignments that are not used anywhere in the code
            // need to run this repeatedly, because deleting one instruction may
            // free another one
            val uses = computeUseCases(instructionList)
            val oldSize = instructionList.instructions.size
            instructionList = instructionList.filterNot(InstructionFilter {
                it is JIRRawAssignInst
                        && it.lhv is JIRRawSimpleValue
                        && it.rhv is JIRRawValue
                        && uses.getOrDefault(it.lhv, 0) == 0
            })
        } while (instructionList.instructions.size != oldSize)

        do {
            // delete the assignments that are mutually dependent only on one another
            // (e.g. `a = b` and `b = a`) and not used anywhere else; also need to run several times
            // because of potential dependencies between such variables
            val assignmentsMap = computeAssignments(instructionList)
            val replacements = assignmentsMap.filterValues { it.size == 1 }.map { it.key to it.value.first() }.toMap()
            instructionList = instructionList
                .filterNot(InstructionFilter {
                    if (it !is JIRRawAssignInst) return@InstructionFilter false
                    val lhv = it.lhv as? JIRRawSimpleValue ?: return@InstructionFilter false
                    val rhv = it.rhv as? JIRRawSimpleValue ?: return@InstructionFilter false
                    replacements[lhv] == rhv && replacements[rhv] == lhv
                })
                .map(ExprMapper(replacements.toMap()))
                .filterNot(InstructionFilter {
                    it is JIRRawAssignInst && it.rhv == it.lhv
                })
        } while (replacements.isNotEmpty())

        do {
            // trying to remove all the simple variables that are equivalent to some other simple variable
            val uses = computeUseCases(instructionList)
            val (replacements, instructionsToDelete) = computeReplacements(instructionList, uses)
            instructionList = instructionList
                .map(ExprMapper(replacements.toMap()))
                .filter(InstructionFilter { it !in instructionsToDelete })
        } while (replacements.isNotEmpty())

        // remove instructions like `a = a`
        instructionList = cleanSelfAssignments(instructionList)
        // fix some typing errors and normalize the types of all local variables
        return normalizeTypes(instructionList)
    }

    private fun computeUseCases(instList: JIRInstListImpl<JIRRawInst>): Map<JIRRawSimpleValue, Set<JIRRawInst>> {
        val uses = hashMapOf<JIRRawSimpleValue, MutableSet<JIRRawInst>>()
        for (inst in instList) {
            when (inst) {
                is JIRRawAssignInst -> {
                    if (inst.lhv is JIRRawComplexValue) {
                        inst.lhv.applyAndGet(SimplifierCollector()) { it.exprs }
                            .forEach {
                                uses.getOrPut(it, ::mutableSetOf).add(inst)
                            }
                    }
                    inst.rhv.applyAndGet(SimplifierCollector()) { it.exprs }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }

                is JIRRawCatchInst -> {}
                else -> {
                    inst.applyAndGet(SimplifierCollector()) { it.exprs }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }
            }
        }
        return uses
    }

    private fun cleanRepeatedAssignments(instList: JIRInstListImpl<JIRRawInst>): JIRInstListImpl<JIRRawInst> {
        val instructions = mutableListOf<JIRRawInst>()
        val equalities = hashMapOf<JIRRawSimpleValue, JIRRawSimpleValue>()
        for (inst in instList) {
            when (inst) {
                is JIRRawAssignInst -> {
                    val lhv = inst.lhv
                    val rhv = inst.rhv
                    if (lhv is JIRRawSimpleValue && rhv is JIRRawSimpleValue) {
                        if (equalities[lhv] != rhv) {
                            equalities[lhv] = rhv
                            instructions += inst
                        }
                    } else {
                        instructions += inst
                    }
                }

                is JIRRawLabelInst -> {
                    instructions += inst
                    equalities.clear()
                }

                else -> instructions += inst
            }
        }
        return JIRInstListImpl(instructions)
    }

    private fun cleanSelfAssignments(instList: JIRInstListImpl<JIRRawInst>): JIRInstListImpl<JIRRawInst> {
        val instructions = mutableListOf<JIRRawInst>()
        for (inst in instList) {
            when (inst) {
                is JIRRawAssignInst -> {
                    if (inst.lhv != inst.rhv) {
                        instructions += inst
                    }
                }

                else -> instructions += inst
            }
        }
        return JIRInstListImpl(instructions)
    }

    private fun computeReplacements(
        instList: JIRInstListImpl<JIRRawInst>,
        uses: Map<JIRRawSimpleValue, Set<JIRRawInst>>
    ): Pair<Map<JIRRawLocalVar, JIRRawValue>, Set<JIRRawInst>> {
        val replacements = mutableMapOf<JIRRawLocalVar, JIRRawValue>()
        val reservedValues = mutableSetOf<JIRRawValue>()
        val replacedInsts = mutableSetOf<JIRRawInst>()

        for (inst in instList) {
            if (inst is JIRRawAssignInst) {
                val rhv = inst.rhv
                if (inst.lhv is JIRRawSimpleValue
                    && rhv is JIRRawLocalVar
                    && uses.getOrDefault(inst.rhv, emptySet()).firstOrNull() == inst
                    && rhv !in reservedValues
                ) {
                    val lhv = inst.lhv
                    val lhvUsage = uses.getOrDefault(lhv, emptySet()).firstOrNull()
                    if (lhvUsage == null || !instList.isBefore(lhvUsage, inst)) {
                        replacements[rhv] = lhv
                        reservedValues += lhv
                        replacedInsts += inst
                    }
                }
            }
        }

        return replacements to replacedInsts
    }

    private fun JIRInstListImpl<JIRRawInst>.isBefore(one: JIRRawInst, another: JIRRawInst): Boolean {
        return indexOf(one) < indexOf(another)
    }

    private fun computeAssignments(instList: JIRInstListImpl<JIRRawInst>): Map<JIRRawSimpleValue, Set<JIRRawSimpleValue>> {
        val assignments = mutableMapOf<JIRRawSimpleValue, MutableSet<JIRRawSimpleValue>>()
        for (inst in instList) {
            if (inst is JIRRawAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv
                if (lhv is JIRRawLocalVar && rhv is JIRRawLocalVar) {
                    assignments.getOrPut(lhv, ::mutableSetOf).add(rhv)
                }
            }
        }
        return assignments
    }

    private fun normalizeTypes(
        instList: JIRInstListImpl<JIRRawInst>
    ): JIRInstListImpl<JIRRawInst> {
        val types = mutableMapOf<JIRRawLocalVar, MutableSet<String>>()
        for (inst in instList) {
            if (inst is JIRRawAssignInst && inst.lhv is JIRRawLocalVar && inst.rhv !is JIRRawNullConstant) {
                types.getOrPut(
                    inst.lhv as JIRRawLocalVar,
                    ::mutableSetOf
                ) += inst.rhv.typeName.typeName
            }
        }
        val replacement = types.filterValues { it.size > 1 }
            .mapValues {
                JIRRawLocalVar(it.key.name, it.key.typeName)
            }
        return instList.map(ExprMapper(replacement.toMap()))
    }
}

private class SimplifierCollector : AbstractFullRawExprSetCollector() {
    val exprs = hashSetOf<JIRRawSimpleValue>()

    override fun ifMatches(expr: JIRRawExpr) {
        if (expr is JIRRawSimpleValue && expr !is JIRRawConstant) {
            exprs.add(expr)
        }
    }

}