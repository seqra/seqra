package org.opentaint.jvm.sast.project.spring

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.jvm.sast.project.OpentaintNonDetermineUtil.addNonDetInstruction
import org.opentaint.jvm.transformer.JSingleInstructionTransformer
import org.opentaint.jvm.transformer.JSingleInstructionTransformer.BlockGenerationContext

class SpringComponentsResolveTransformer: JIRInstExtFeature {
    private var _context: Any? = CONTEXT_NOT_INITIALIZED

    fun initialize(context: SpringWebProjectContext?) {
        this._context = context
    }

    fun context(): SpringWebProjectContext? {
        check(_context !== CONTEXT_NOT_INITIALIZED) {
            "Spring context is not initialized yet."
        }

        return _context as? SpringWebProjectContext
    }

    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        val ctx = context() ?: return list

        val componentActions = mutableListOf<ComponentAction>()

        for (inst in list) {
            if (inst !is JIRAssignInst) continue

            val rhsFiled = inst.rhv as? JIRFieldRef
            val lhsField = inst.lhv as? JIRFieldRef
            val typedField = lhsField?.field ?: rhsFiled?.field ?: continue
            val field = typedField.field
            val dependencies = ctx.fieldDependencies[field]
                ?: continue

            if (rhsFiled != null) {
                componentActions += ComponentAction.ReadComponentField(inst, inst.lhv, typedField, dependencies)
            }

            if (lhsField != null) {
                val value = inst.rhv as? JIRValue ?: continue
                componentActions += ComponentAction.WriteComponentField(inst, value, typedField, dependencies)
            }
        }

        if (componentActions.isEmpty()) return list

        val transformer = JSingleInstructionTransformer(list)
        for (action in componentActions) {
            transformer.generateReplacementBlock(action.inst) { componentAction(ctx, action) }
        }

        return transformer.buildInstList()
    }

    private fun BlockGenerationContext.componentAction(ctx: SpringWebProjectContext, action: ComponentAction) {
        val componentRefs = action.components.map { ctx.registryFieldRef(it) }

        when (action) {
            is ComponentAction.ReadComponentField -> {
                addInstruction { loc ->
                    JIRAssignInst(loc, action.readTo, JIRNullConstant(action.readTo.type))
                }

                if (INCLUDE_COMPONENT_ASSIGN) {
                    addNonDetInstruction { loc -> JIRAssignInst(loc, action.readTo, action.inst.rhv) }
                }

                componentRefs.forEach { cmp ->
                    addNonDetInstruction { loc -> JIRAssignInst(loc, action.readTo, cmp) }
                }
            }

            is ComponentAction.WriteComponentField -> {
                if (INCLUDE_COMPONENT_ASSIGN) {
                    addInstruction { loc -> JIRAssignInst(loc, action.inst.lhv, action.writeFrom) }
                }

                componentRefs.forEach { cmp ->
                    addNonDetInstruction { loc -> JIRAssignInst(loc, cmp, action.writeFrom) }
                }
            }
        }
    }

    private sealed interface ComponentAction {
        val inst: JIRInst
        val components: Set<JIRClassOrInterface>

        data class ReadComponentField(
            override val inst: JIRAssignInst,
            val readTo: JIRValue,
            val field: JIRTypedField,
            override val components: Set<JIRClassOrInterface>
        ): ComponentAction

        data class WriteComponentField(
            override val inst: JIRAssignInst,
            val writeFrom: JIRValue,
            val field: JIRTypedField,
            override val components: Set<JIRClassOrInterface>
        ): ComponentAction
    }

    companion object {
        private const val INCLUDE_COMPONENT_ASSIGN = false
        private val CONTEXT_NOT_INITIALIZED = Any()
    }
}
