package org.opentaint.ir.api.jvm.analysis

import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRLocal

interface JIRPointsToAnalysis<Context> {

    /** @return set of objects pointed to by variable [local] in context [context].  */
    fun reachingObjects(local: JIRLocal): JIRPointsToSet = reachingObjects(local, null)
    fun reachingObjects(local: JIRLocal, context: Context?): JIRPointsToSet

    /** @return set of objects pointed to by field  */
    fun reachingObjects(field: JIRField): JIRPointsToSet

    /**
     * @return set of objects pointed to by instance field f of the objects in the PointsToSet [set].
     */
    fun reachingObjects(set: JIRPointsToSet, field: JIRField): JIRPointsToSet

    /**
     * @return the set of objects pointed to by instance field [field] of the objects pointed to by [local] in context [context].
     */
    fun reachingObjects(local: JIRLocal, field: JIRField): JIRPointsToSet = reachingObjects(local, field, null)
    fun reachingObjects(local: JIRLocal, field: JIRField, context: Context? = null): JIRPointsToSet

    /**
     * @return the set of objects pointed to by elements of the arrays in the PointsToSet [set].
     */
    fun reachingObjectsOfArrayElement(set: JIRPointsToSet): JIRPointsToSet

}

//TODO check api for consistency
interface JIRPointsToSet {

    val isEmpty: Boolean

    fun intersects(other: JIRPointsToSet): Boolean

    /** all possible runtime types of objects in the set.  */
    val possibleTypes: Set<JIRType>

    /**
     * If this points-to set consists entirely of string constants, returns a set of these constant strings.
     *
     * If this point-to set may contain something other than constant strings, returns null.
     */
    val possibleStrings: Set<String>?

    /**
     * If this points-to set consists entirely of objects of type java.lang.Class of a known class, returns a set of these constant strings.
     *
     * If this point-to set may contain something else, returns null.
     */
    val possibleClasses: Set<JIRClassOrInterface>?

}