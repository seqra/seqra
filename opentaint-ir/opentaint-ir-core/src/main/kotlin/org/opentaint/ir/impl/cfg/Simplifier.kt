
package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRawAssignInst
import org.opentaint.ir.api.JIRRawCatchInst
import org.opentaint.ir.api.JIRRawComplexValue
import org.opentaint.ir.api.JIRRawConstant
import org.opentaint.ir.api.JIRRawInst
import org.opentaint.ir.api.JIRRawInstList
import org.opentaint.ir.api.JIRRawLabelInst
import org.opentaint.ir.api.JIRRawLocal
import org.opentaint.ir.api.JIRRawNullConstant
import org.opentaint.ir.api.JIRRawSimpleValue
import org.opentaint.ir.api.JIRRawValue
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.cfg.ext.applyAndGet
import org.opentaint.ir.api.cfg.ext.filter
import org.opentaint.ir.api.cfg.ext.filterNot
import org.opentaint.ir.api.cfg.ext.map
import org.opentaint.ir.api.ext.findCommonSupertype
import org.opentaint.ir.impl.cfg.util.ExprMapper
import org.opentaint.ir.impl.cfg.util.FullExprSetCollector
import org.opentaint.ir.impl.cfg.util.InstructionFilter
import org.opentaint.ir.impl.cfg.util.typeName

/**
 * a class that simplifies the instruction list after construction
 * a simplification process is required, because the construction process
 * naturally introduces some redundancy into the code (mainly because of
 * the frames merging)
 */
internal class Simplifier {

    fun simplify(jirClasspath: JIRClasspath, instList: JIRRawInstList): JIRRawInstList {
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
        instructionList = normalizeTypes(jirClasspath, instructionList)

        return instructionList
    }


    private fun computeUseCases(instList: JIRRawInstList): Map<JIRRawSimpleValue, Set<JIRRawInst>> {
        val uses = mutableMapOf<JIRRawSimpleValue, MutableSet<JIRRawInst>>()
        for (inst in instList) {
            when (inst) {
                is JIRRawAssignInst -> {
                    if (inst.lhv is JIRRawComplexValue) {
                        inst.lhv.applyAndGet(FullExprSetCollector()) { it.exprs }
                            .filterIsInstance<JIRRawSimpleValue>()
                            .filter { it !is JIRRawConstant }
                            .forEach {
                                uses.getOrPut(it, ::mutableSetOf).add(inst)
                            }
                    }
                    inst.rhv.applyAndGet(FullExprSetCollector()) { it.exprs }
                        .filterIsInstance<JIRRawSimpleValue>()
                        .filter { it !is JIRRawConstant }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }

                is JIRRawCatchInst -> {}
                else -> {
                    inst.operands
                        .flatMapTo(mutableSetOf()) { expr -> expr.applyAndGet(FullExprSetCollector()) { it.exprs } }
                        .filterIsInstance<JIRRawSimpleValue>()
                        .filter { it !is JIRRawConstant }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }
            }
        }
        return uses
    }

    private fun cleanRepeatedAssignments(instList: JIRRawInstList): JIRRawInstList {
        val instructions = mutableListOf<JIRRawInst>()
        val equalities = mutableMapOf<JIRRawSimpleValue, JIRRawSimpleValue>()
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
        return JIRRawInstList(instructions)
    }

    private fun cleanSelfAssignments(instList: JIRRawInstList): JIRRawInstList {
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
        return JIRRawInstList(instructions)
    }

    private fun computeReplacements(
        instList: JIRRawInstList,
        uses: Map<JIRRawSimpleValue, Set<JIRRawInst>>
    ): Pair<Map<JIRRawLocal, JIRRawValue>, Set<JIRRawInst>> {
        val replacements = mutableMapOf<JIRRawLocal, JIRRawValue>()
        val reservedValues = mutableSetOf<JIRRawValue>()
        val replacedInsts = mutableSetOf<JIRRawInst>()

        for (inst in instList) {
            if (inst is JIRRawAssignInst) {
                val rhv = inst.rhv
                if (inst.lhv is JIRRawSimpleValue
                    && rhv is JIRRawLocal
                    && uses.getOrDefault(inst.rhv, emptySet()).firstOrNull() == inst
                    && rhv !in reservedValues
                ) {
                    replacements[rhv] = inst.lhv
                    reservedValues += inst.lhv
                    replacedInsts += inst
                }
            }
        }

        return replacements to replacedInsts
    }

    private fun computeAssignments(instList: JIRRawInstList): Map<JIRRawSimpleValue, Set<JIRRawSimpleValue>> {
        val assignments = mutableMapOf<JIRRawSimpleValue, MutableSet<JIRRawSimpleValue>>()
        for (inst in instList) {
            if (inst is JIRRawAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv
                if (lhv is JIRRawLocal && rhv is JIRRawLocal) {
                    assignments.getOrPut(lhv, ::mutableSetOf).add(rhv)
                }
            }
        }
        return assignments
    }

    private fun normalizeTypes(jirClasspath: JIRClasspath, instList: JIRRawInstList): JIRRawInstList {
        val types = mutableMapOf<JIRRawLocal, MutableSet<JIRType>>()
        for (inst in instList) {
            if (inst is JIRRawAssignInst && inst.lhv is JIRRawLocal && inst.rhv !is JIRRawNullConstant) {
                types.getOrPut(
                    inst.lhv as JIRRawLocal,
                    ::mutableSetOf
                ) += jirClasspath.findTypeOrNull(inst.rhv.typeName.typeName)
                    ?: error("Could not find type")
            }
        }
        val replacement = types.filterValues { it.size > 1 }
            .mapValues {
                val supertype = jirClasspath.findCommonSupertype(it.value)
                    ?: error("Could not find common supertype of ${it.value.joinToString { it.typeName }}")
                JIRRawLocal(it.key.name, supertype.typeName.typeName())
            }
        return instList.map(ExprMapper(replacement.toMap()))
    }
}
