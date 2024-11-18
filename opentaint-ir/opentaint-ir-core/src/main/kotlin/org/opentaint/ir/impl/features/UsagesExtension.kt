package org.opentaint.ir.impl.features

import org.objectweb.asm.Opcodes
import org.opentaint.ir.api.FieldUsageMode
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.findFieldOrNull
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.api.isPackagePrivate
import org.opentaint.ir.api.isPrivate
import org.opentaint.ir.api.isStatic
import org.opentaint.ir.api.packageName

class SyncUsagesExtension(private val hierarchyExtension: HierarchyExtension, private val cp: JIRClasspath) {

    /**
     * find all methods that call this method
     *
     * @param method method
     */
    fun findUsages(method: JIRMethod): Sequence<JIRMethod> {
        val maybeHierarchy = maybeHierarchy(method.enclosingClass, method.isPrivate) {
            it.findMethodOrNull(method.name, method.description).let {
                it == null || !it.isOverriddenBy(method)
            } // no overrides// no override
        }

        val opcodes = when (method.isStatic) {
            true -> setOf(Opcodes.INVOKESTATIC)
            else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL)
        }
        return findMatches(maybeHierarchy, method = method, opcodes = opcodes)

    }

    /**
     * find all methods that directly modifies field
     *
     * @param field field
     * @param mode mode of search
     */
    fun findUsages(field: JIRField, mode: FieldUsageMode): Sequence<JIRMethod> {
        val maybeHierarchy = maybeHierarchy(field.enclosingClass, field.isPrivate) {
            it.findFieldOrNull(field.name).let {
                it == null || !it.isOverriddenBy(field)
            } // no overrides
        }
        val isStatic = field.isStatic
        val opcode = when {
            isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
            !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
            isStatic && mode == FieldUsageMode.READ -> Opcodes.GETSTATIC
            !isStatic && mode == FieldUsageMode.READ -> Opcodes.GETFIELD
            else -> return emptySequence()
        }

        return findMatches(maybeHierarchy, field = field, opcodes = listOf(opcode))
    }

    private fun maybeHierarchy(
        enclosingClass: JIRClassOrInterface,
        private: Boolean,
        matcher: (JIRClassOrInterface) -> Boolean
    ): Set<JIRClassOrInterface> {
        return when {
            private -> hashSetOf(enclosingClass)
            else -> hierarchyExtension.findSubClasses(enclosingClass.name, true).filter(matcher)
                .toHashSet() + enclosingClass
        }
    }


    private fun findMatches(
        hierarchy: Set<JIRClassOrInterface>,
        method: JIRMethod? = null,
        field: JIRField? = null,
        opcodes: Collection<Int>
    ): Sequence<JIRMethod> {
        val list = hierarchy.map {
            Usages.syncQuery(
                cp, UsageFeatureRequest(
                    methodName = method?.name,
                    description = method?.description,
                    field = field?.name,
                    opcodes = opcodes,
                    className = it.name
                )
            ).flatMap {
                cp.toJcClass(it.source)
                    .declaredMethods
                    .slice(it.offsets.map { it.toInt() })
            }
        }

        return sequence {
            list.forEach {
                yieldAll(it)
            }
        }
    }

    private fun JIRMethod.isOverriddenBy(method: JIRMethod): Boolean {
        if (name == method.name && description == method.description) {
            return when {
                isPrivate -> false
                isPackagePrivate -> enclosingClass.packageName == method.enclosingClass.packageName
                else -> true
            }
        }
        return false
    }

    private fun JIRField.isOverriddenBy(field: JIRField): Boolean {
        if (name == field.name) {
            return when {
                isPrivate -> false
                isPackagePrivate -> enclosingClass.packageName == field.enclosingClass.packageName
                else -> true
            }
        }
        return false
    }
}


suspend fun JIRClasspath.usagesExtension(): SyncUsagesExtension {
    if (!db.isInstalled(Usages)) {
        throw IllegalStateException("This extension requires `Usages` feature to be installed")
    }
    return SyncUsagesExtension(hierarchyExt(), this)
}

suspend fun JIRClasspath.findUsages(method: JIRMethod) = usagesExtension().findUsages(method)
suspend fun JIRClasspath.findUsages(field: JIRField, mode: FieldUsageMode) = usagesExtension().findUsages(field, mode)
