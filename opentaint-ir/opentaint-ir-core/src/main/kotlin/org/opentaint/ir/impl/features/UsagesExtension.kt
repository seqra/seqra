@file:JvmName("JIRUsages")

package org.opentaint.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.jvm.FieldUsageMode
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.HierarchyExtension
import org.opentaint.ir.api.jvm.ext.findDeclaredFieldOrNull
import org.opentaint.ir.api.jvm.ext.findDeclaredMethodOrNull
import org.opentaint.ir.api.jvm.ext.packageName
import org.objectweb.asm.Opcodes
import java.util.concurrent.Future

class SyncUsagesExtension(private val hierarchyExtension: HierarchyExtension, private val cp: JIRClasspath) {

    /**
     * find all methods that call this method
     *
     * @param method method
     */
    fun findUsages(method: JIRMethod): Sequence<JIRMethod> {
        val maybeHierarchy = maybeHierarchy(method.enclosingClass, method.isPrivate) {
            it.findDeclaredMethodOrNull(method.name, method.description).let {
                it == null || !it.isOverriddenBy(method)
            } // no overrides
        }

        val opcodes = when (method.isStatic) {
            true -> setOf(Opcodes.INVOKESTATIC)
            else -> setOf(Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE)
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
            it.findDeclaredFieldOrNull(field.name).let {
                it == null || !it.isOverriddenBy(field)
            } // no overrides
        }
        val isStatic = field.isStatic
        val opcode = when {
            isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTSTATIC
            !isStatic && mode == FieldUsageMode.WRITE -> Opcodes.PUTFIELD
            isStatic && mode == FieldUsageMode.READ -> Opcodes.GFrontendTATIC
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
        return Usages.syncQuery(
            cp, UsageFeatureRequest(
                methodName = method?.name,
                description = method?.description,
                field = field?.name,
                opcodes = opcodes,
                className = hierarchy.map { it.name }.toSet()
            )
        ).flatMap {
            cp.toJIRClass(it.source)
                .declaredMethods
                .slice(it.offsets.map { it.toInt() })
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

suspend fun JIRClasspath.usagesExt(): SyncUsagesExtension {
    if (!db.isInstalled(Usages)) {
        throw IllegalStateException("This extension requires `Usages` feature to be installed")
    }
    return SyncUsagesExtension(hierarchyExt(), this)
}

fun JIRClasspath.asyncUsages(): Future<SyncUsagesExtension> = GlobalScope.future { usagesExt() }

suspend fun JIRClasspath.findUsages(method: JIRMethod) = usagesExt().findUsages(method)
suspend fun JIRClasspath.findUsages(field: JIRField, mode: FieldUsageMode) = usagesExt().findUsages(field, mode)
