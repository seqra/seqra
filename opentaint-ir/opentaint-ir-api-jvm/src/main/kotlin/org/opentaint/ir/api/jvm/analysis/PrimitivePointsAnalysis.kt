package org.opentaint.ir.api.jvm.analysis

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.ext.objectType

class FullObjectsSet(type: JIRType) : JIRPointsToSet {

    override val possibleTypes: Set<JIRType> = setOf(type)

    override val isEmpty: Boolean
        get() = possibleTypes.isEmpty()

    override fun intersects(other: JIRPointsToSet) = false

    override val possibleStrings: Set<String>? = null
    override val possibleClasses: Set<JIRClassOrInterface>? = null
}

class PrimitivePointsAnalysis(private val classpath: JIRClasspath) : JIRPointsToAnalysis<JIRInst> {

    override fun reachingObjects(local: JIRLocal, context: JIRInst?): JIRPointsToSet {
        return FullObjectsSet(local.type)
    }

    override fun reachingObjects(field: JIRField): JIRPointsToSet {
        return FullObjectsSet(classpath.findTypeOrNull(field.type.typeName) ?: classpath.objectType)
    }

    override fun reachingObjects(set: JIRPointsToSet, field: JIRField): JIRPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjects(local: JIRLocal, field: JIRField, context: JIRInst?): JIRPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjectsOfArrayElement(set: JIRPointsToSet): JIRPointsToSet {
        return FullObjectsSet(classpath.objectType)
    }
}
