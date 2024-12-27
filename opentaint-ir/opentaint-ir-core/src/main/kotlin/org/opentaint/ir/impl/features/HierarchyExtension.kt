@file:JvmName("JIRHierarchies")
package org.opentaint.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.*
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.HierarchyExtension
import org.opentaint.ir.api.ext.findDeclaredMethodOrNull
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.storage.BatchedSequence
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import java.util.concurrent.Future

@Suppress("SqlResolve")
class HierarchyExtensionImpl(private val cp: JIRClasspath) : HierarchyExtension {

    companion object {
        private fun allHierarchyQuery(locationIds: String, sinceId: Long?) = """
            WITH RECURSIVE Hierarchy(class_name_id, class_id) AS (
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId)
                UNION ALL
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    JOIN Hierarchy ON Hierarchy.class_name_id = ClassHierarchies.super_id
                    WHERE $sinceId is null or ClassHierarchies.id > $sinceId)
            SELECT DISTINCT Classes.id, Classes.location_id,  Symbols.name as name_name, Classes.bytecode from Hierarchy
                JOIN Classes ON Classes.id = hierarchy.class_id
                JOIN Symbols ON Symbols.id = Classes.name
             WHERE location_id in ($locationIds)
        """.trimIndent()

        private fun directSubClassesQuery(locationIds: String, sinceId: Long?) = """
            SELECT Classes.id, Classes.location_id, SymbolsName.name as name_name, Classes.bytecode FROM ClassHierarchies
                JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                JOIN Symbols as SymbolsName ON SymbolsName.id = Classes.name
                JOIN Classes ON Classes.id = ClassHierarchies.class_id
            WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId) AND Classes.location_id in ($locationIds) 
        """.trimIndent()

    }

    override fun findSubClasses(name: String, allHierarchy: Boolean): Sequence<JIRClassOrInterface> {
        val jIRClass = cp.findClassOrNull(name) ?: return emptySequence()
        if (jIRClass.isFinal) {
            return emptySequence()
        }
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(name, allHierarchy, false)
        }
        return findSubClasses(jIRClass, allHierarchy)
    }

    override fun findSubClasses(jIRClass: JIRClassOrInterface, allHierarchy: Boolean): Sequence<JIRClassOrInterface> {
        if (jIRClass.isFinal) {
            return emptySequence()
        }
        return findSubClasses(jIRClass, allHierarchy, false)
    }

    override fun findOverrides(jIRMethod: JIRMethod, includeAbstract: Boolean): Sequence<JIRMethod> {
        if (jIRMethod.isFinal || jIRMethod.isConstructor || jIRMethod.isStatic || jIRMethod.isClassInitializer) {
            return emptySequence()
        }
        val desc = jIRMethod.description
        val name = jIRMethod.name
        return findSubClasses(jIRMethod.enclosingClass, allHierarchy = true, true)
            .mapNotNull { it.findDeclaredMethodOrNull(name, desc) }
            .filter { !it.isPrivate }
    }

    private fun findSubClasses(
        jIRClass: JIRClassOrInterface,
        allHierarchy: Boolean,
        full: Boolean
    ): Sequence<JIRClassOrInterface> {
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(jIRClass.name, allHierarchy, full)
        }
        val name = jIRClass.name

        return cp.subClasses(name, allHierarchy, full).map { record ->
            cp.toJIRClass(
                PersistenceClassSource(
                    classpath = cp,
                    locationId = record.locationId,
                    classId = record.id,
                    className = record.name
                ).bind(record.byteCode)
            )
        }
    }

    private fun JIRClasspath.subClasses(name: String, allHierarchy: Boolean, full: Boolean): Sequence<ClassRecord> {
        val locationIds = registeredLocations.joinToString(", ") { it.id.toString() }
        return BatchedSequence(50) { offset, batchSize ->
            val query = when {
                allHierarchy -> allHierarchyQuery(locationIds, offset)
                else -> directSubClassesQuery(locationIds, offset)
            }
            db.persistence.read {
                val cursor = it.fetchLazy(query, name)
                cursor.fetchNext(batchSize).map { record ->
                    val id = record.get(CLASSES.ID)!!
                    id to ClassRecord(
                        id = record.get(CLASSES.ID)!!,
                        name = record.get("name_name") as String,
                        locationId = record.get(CLASSES.LOCATION_ID)!!,
                        byteCode = if (full) record.get(CLASSES.BYTECODE) else null
                    )
                }.also {
                    cursor.close()
                }
            }
        }

    }
}

private class ClassRecord(
    val id: Long,
    val name: String,
    val locationId: Long,
    val byteCode: ByteArray? = null
)

suspend fun JIRClasspath.hierarchyExt(): HierarchyExtensionImpl {
    db.awaitBackgroundJobs()
    return HierarchyExtensionImpl(this)
}

fun JIRClasspath.asyncHierarchy(): Future<HierarchyExtension> = GlobalScope.future { hierarchyExt() }

