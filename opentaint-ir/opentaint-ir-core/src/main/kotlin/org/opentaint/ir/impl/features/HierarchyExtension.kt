@file:JvmName("JIRHierarchies")

package org.opentaint.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.HierarchyExtension
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.ext.findDeclaredMethodOrNull
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.storage.BatchedSequence
import org.opentaint.ir.impl.storage.defaultBatchSize
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.Condition
import org.jooq.Record3
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL
import java.util.concurrent.Future

@Suppress("SqlResolve")
class HierarchyExtensionImpl(private val cp: JIRClasspath) : HierarchyExtension {

    companion object {
        private fun allHierarchyQuery(locationIds: String, sinceId: Long?) = """
            WITH RECURSIVE Hierarchy(class_name_id, class_id) AS (
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    WHERE Symbols.name = ?
                UNION ALL
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    JOIN Hierarchy ON Hierarchy.class_name_id = ClassHierarchies.super_id)
            SELECT DISTINCT Classes.id, Classes.location_id,  Symbols.name as name_name, Classes.bytecode from Hierarchy
                JOIN Classes ON Classes.id = hierarchy.class_id
                JOIN Symbols ON Symbols.id = Classes.name
             WHERE location_id in ($locationIds) and ($sinceId is null or Hierarchy.class_id > $sinceId)
             ORDER BY Classes.id
        """.trimIndent()

        private fun directSubClassesQuery(locationIds: String, sinceId: Long?) = """
            SELECT Classes.id, Classes.location_id, SymbolsName.name as name_name, Classes.bytecode FROM ClassHierarchies
                JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                JOIN Symbols as SymbolsName ON SymbolsName.id = Classes.name
                JOIN Classes ON Classes.id = ClassHierarchies.class_id
            WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId) AND Classes.location_id in ($locationIds)
            ORDER BY Classes.id
        """.trimIndent()

    }

    override fun findSubClasses(name: String, allHierarchy: Boolean, includeOwn: Boolean): Sequence<JIRClassOrInterface> {
        val jIRClass = cp.findClassOrNull(name) ?: return emptySequence()
        return when {
            jIRClass.isFinal -> emptySequence()
            cp.db.isInstalled(InMemoryHierarchy) -> cp.findSubclassesInMemory(name, allHierarchy, false)
            else -> findSubClasses(jIRClass, allHierarchy, false)
        }.appendOwn(jIRClass, includeOwn)
    }

    private fun Sequence<JIRClassOrInterface>.appendOwn(root: JIRClassOrInterface, includeOwn: Boolean): Sequence<JIRClassOrInterface> {
        return if(includeOwn) sequenceOf(root) + this else this
    }

    override fun findSubClasses(jIRClass: JIRClassOrInterface, allHierarchy: Boolean, includeOwn: Boolean): Sequence<JIRClassOrInterface> {
        if (jIRClass.isFinal) {
            return emptySequence<JIRClassOrInterface>().appendOwn(jIRClass, includeOwn)
        }
        return when {
            jIRClass.isFinal -> emptySequence()
            else -> explicitSubClasses(jIRClass, allHierarchy, false)
        }.appendOwn(jIRClass, includeOwn)
    }

    override fun findOverrides(jIRMethod: JIRMethod, includeAbstract: Boolean): Sequence<JIRMethod> {
        if (jIRMethod.isFinal || jIRMethod.isConstructor || jIRMethod.isStatic || jIRMethod.isClassInitializer) {
            return emptySequence()
        }
        val desc = jIRMethod.description
        val name = jIRMethod.name
        return explicitSubClasses(jIRMethod.enclosingClass, allHierarchy = true, true)
            .mapNotNull { it.findDeclaredMethodOrNull(name, desc) }
            .filter { !it.isPrivate }
    }

    private fun explicitSubClasses(
        jIRClass: JIRClassOrInterface,
        allHierarchy: Boolean,
        full: Boolean
    ): Sequence<JIRClassOrInterface> {
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(jIRClass.name, allHierarchy, full)
        }
        val name = jIRClass.name

        return cp.subClasses(name, allHierarchy).map { cp.toJIRClass(it) }
    }

    private fun JIRClasspath.subClasses(
        name: String,
        allHierarchy: Boolean
    ): Sequence<PersistenceClassSource> {
        val locationIds = registeredLocations.joinToString(", ") { it.id.toString() }
        if (name == JAVA_OBJECT) {
            return allClassesExceptObject(!allHierarchy)
        }
        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
            val query = when {
                allHierarchy -> allHierarchyQuery(locationIds, offset)
                else -> directSubClassesQuery(locationIds, offset)
            }
            db.persistence.read {
                val cursor = it.fetchLazy(query, name)
                cursor.fetchNext(batchSize).map { record ->
                    val id = record.get(CLASSES.ID)!!
                    id to PersistenceClassSource(
                        db = db,
                        classId = record.get(CLASSES.ID)!!,
                        className = record.get("name_name") as String,
                        locationId = record.get(CLASSES.LOCATION_ID)!!
                    ).bind(record.get(CLASSES.BYTECODE))
                }.also {
                    cursor.close()
                }
            }
        }

    }
}

suspend fun JIRClasspath.hierarchyExt(): HierarchyExtensionImpl {
    db.awaitBackgroundJobs()
    return HierarchyExtensionImpl(this)
}

fun JIRClasspath.asyncHierarchy(): Future<HierarchyExtension> = GlobalScope.future { hierarchyExt() }

private fun SelectConditionStep<Record3<Long?, String?, Long?>>.batchingProcess(cp: JIRClasspath, batchSize: Int): List<Pair<Long, PersistenceClassSource>>{
    return orderBy(CLASSES.ID)
        .limit(batchSize)
        .fetch()
        .mapNotNull { (classId, className, locationId) ->
            classId!! to PersistenceClassSource(
                db = cp.db,
                classId = classId,
                className = className!!,
                locationId = locationId!!
            )
        }
}

private fun List<Long>.where(offset: Long?): Condition {
    return when (offset) {
        null -> CLASSES.LOCATION_ID.`in`(this)
        else -> CLASSES.LOCATION_ID.`in`(this).and(CLASSES.ID.greaterThan(offset))
    }
}

internal fun JIRClasspath.allClassesExceptObject(direct: Boolean): Sequence<PersistenceClassSource> {
    val locationIds = registeredLocations.map { it.id }
    if (direct) {
        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
            db.persistence.read { jooq ->
                val whereCondition = locationIds.where(offset)
                jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                    .from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(
                        whereCondition
                            .and(DSL.notExists(
                                jooq.select(CLASSHIERARCHIES.ID).from(CLASSHIERARCHIES)
                                .where(CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID).and(CLASSHIERARCHIES.IS_CLASS_REF.eq(true)))
                            ))
                            .and(SYMBOLS.NAME.notEqual(JAVA_OBJECT))
                    )
                    .batchingProcess(this, batchSize)
                }
            }
        }
        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
            db.persistence.read { jooq ->
                val whereCondition = locationIds.where(offset)

                jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                    .from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(whereCondition.and(SYMBOLS.NAME.notEqual(JAVA_OBJECT)))
                    .batchingProcess(this, batchSize)
            }
        }
    }
