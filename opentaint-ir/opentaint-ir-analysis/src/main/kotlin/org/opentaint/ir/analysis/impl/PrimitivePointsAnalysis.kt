package org.opentaint.ir.analysis.impl

import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRField
import org.opentaint.opentaint-ir.api.JIRType
import org.opentaint.opentaint-ir.api.analysis.JIRPointsToAnalysis
import org.opentaint.opentaint-ir.api.analysis.JIRPointsToSet
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.cfg.JIRLocal
import org.opentaint.opentaint-ir.api.ext.anyType

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
        return FullObjectsSet(classpath.findTypeOrNull(field.type.typeName) ?: classpath.anyType())
    }

    override fun reachingObjects(set: JIRPointsToSet, field: JIRField): JIRPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjects(local: JIRLocal, field: JIRField, context: JIRInst?): JIRPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjectsOfArrayElement(set: JIRPointsToSet): JIRPointsToSet {
        return FullObjectsSet(classpath.anyType())
    }
}