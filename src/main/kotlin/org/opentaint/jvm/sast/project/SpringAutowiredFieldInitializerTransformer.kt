package org.opentaint.jvm.sast.project

import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRInstExtFeature
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstList
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.types.JIRTypedFieldImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import org.opentaint.dataflow.jvm.util.JIRInstListBuilder

class SpringAutowiredFieldInitializerTransformer : JIRInstExtFeature {
    private lateinit var projectClasses: ProjectClasses

    fun init(project: ProjectClasses) {
        this.projectClasses = project
    }

    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        if (!method.isConstructor) return list

        val cls = method.enclosingClass
        if (cls.declaration.location !in projectClasses.projectLocations) return list

        val autowiredFields = cls.declaredFields.filter { field ->
            field.annotations.any { it.isSpringAutowiredAnnotation() }
        }

        if (autowiredFields.isEmpty()) return list

        val mutableInstList = list.instructions.toMutableList()
        val returnInst = mutableInstList.singleOrNull { it is JIRReturnInst } ?: TODO("Multiple return")
        val returnLoc = returnInst.location

        if (returnLoc.index == mutableInstList.lastIndex) {
            mutableInstList.removeLast()
        } else {
            val afterLastInst = JIRGotoInst(returnInst.location, JIRInstRef(mutableInstList.size))
            mutableInstList[returnInst.location.index] = afterLastInst
        }

        val builder = JIRInstListBuilder(mutableInstList)

        for (field in autowiredFields) {
            builder.generateFieldInitializer(method, field)
        }

        builder.addInstWithLocation(method) { loc ->
            JIRReturnInst(loc, returnValue = null)
        }

        return builder
    }

    private fun JIRInstListBuilder.generateFieldInitializer(method: JIRMethod, field: JIRField) {
        val clsType = field.enclosingClass.toType()
        val typedField = JIRTypedFieldImpl(clsType, field, JIRSubstitutorImpl.empty)

        val fieldType = typedField.type as? JIRClassType ?: return
        val componentInstance = loadSpringComponent(method, fieldType.jirClass, field.name)

        addInstWithLocation(method) { loc ->
            val fieldRef = JIRFieldRef(JIRThis(clsType), typedField)
            JIRAssignInst(loc, fieldRef, componentInstance)
        }
    }
}
