package org.opentaint.ir.impl.index

import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.opentaint.ir.api.ArrayClassId
import org.opentaint.ir.api.ClassId
import org.opentaint.ir.api.Classpath
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.MethodId
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.findMethodOrNull
import org.opentaint.ir.impl.storage.Classes
import org.opentaint.ir.impl.storage.SymbolEntity
import org.opentaint.ir.impl.storage.Symbols

class HierarchyExtensionImpl(private val db: JIRDB, private val cp: Classpath) : HierarchyExtension {

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<ClassId> {
        val classId = cp.findClassOrNull(name) ?: return emptyList()
        return findSubClasses(classId, allHierarchy)
    }

    override suspend fun findSubClasses(classId: ClassId, allHierarchy: Boolean): List<ClassId> {
        if (classId is ArrayClassId) {
            return emptyList()
        }
        db.awaitBackgroundJobs()
        val name = classId.name

        return subClasses(name, allHierarchy).mapNotNull { cp.findClassOrNull(it) }
    }

    override suspend fun findOverrides(methodId: MethodId): List<MethodId> {
        val desc = methodId.description()
        val name = methodId.name
        val subClasses = cp.findSubClasses(methodId.classId, allHierarchy = true)
        return subClasses.mapNotNull {
            it.findMethodOrNull(name, desc) // todo this is wrong
        }
    }

    private fun subClasses(name: String, allHierarchy: Boolean): List<String> {
        val subTypes = transaction {
            val nameEntity = SymbolEntity.find { Symbols.name eq name }.firstOrNull()
            if (nameEntity == null) {
                emptyList()
            } else {
                Classes.join(
                    Symbols,
                    joinType = JoinType.INNER,
                    onColumn = Classes.superClass,
                    otherColumn = Symbols.id
                ).slice(Classes.name).select {
                    Classes.superClass eq nameEntity.id
                }.map { it[Symbols.name] }.toHashSet()
            }

        }
        if (allHierarchy) {
            return (subTypes + subTypes.flatMap { subClasses(it, true) }).toPersistentList()
        }
        return subTypes.toPersistentList()
    }

}


val Classpath.hierarchyExt: HierarchyExtensionImpl
    get() {
        return HierarchyExtensionImpl(db, this)
    }