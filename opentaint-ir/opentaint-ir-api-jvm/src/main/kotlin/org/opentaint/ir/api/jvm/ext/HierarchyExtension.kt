package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod

/**
 * hierarchy extension for classpath
 */
interface HierarchyExtension {

    /**
     * find all subclasses or implementations if name points to interface. If [entireHierarchy] is true then search
     * will be done recursively
     *
     * @return list with unique ClassId
     */
    fun findSubClasses(
        name: String,
        entireHierarchy: Boolean,
        includeOwn: Boolean = false
    ): Sequence<JIRClassOrInterface>

    /**
     * find all subclasses or implementations if name points to interface. If [entireHierarchy] is true then search
     * will be done recursively
     *
     * @return list with unique ClassId
     */
    fun findSubClasses(
        jIRClass: JIRClassOrInterface,
        entireHierarchy: Boolean,
        includeOwn: Boolean = false
    ): Sequence<JIRClassOrInterface>

    /**
     * find overrides of current method
     * @return list with unique methods overriding current
     */
    fun findOverrides(jIRMethod: JIRMethod, includeAbstract: Boolean = true): Sequence<JIRMethod>

}
