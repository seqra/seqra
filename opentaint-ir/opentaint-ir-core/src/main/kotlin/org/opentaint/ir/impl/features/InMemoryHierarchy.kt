package org.opentaint.opentaint-ir.impl.features

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.opentaint.opentaint-ir.api.ByteCodeIndexer
import org.opentaint.opentaint-ir.api.ClassSource
import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRDatabase
import org.opentaint.opentaint-ir.api.JIRDatabasePersistence
import org.opentaint.opentaint-ir.api.JIRFeature
import org.opentaint.opentaint-ir.api.JIRSignal
import org.opentaint.opentaint-ir.api.RegisteredLocation
import org.opentaint.opentaint-ir.impl.fs.PersistenceClassSource
import org.opentaint.opentaint-ir.impl.fs.className
import org.opentaint.opentaint-ir.impl.storage.BatchedSequence
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.SYMBOLS
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

typealias InMemoryHierarchyCache = ConcurrentHashMap<Long, ConcurrentHashMap<Long, MutableSet<Long>>>

private val objectJvmName = Type.getInternalName(Any::class.java)

class InMemoryHierarchyIndexer(
    private val persistence: JIRDatabasePersistence,
    private val location: RegisteredLocation,
    private val hierarchy: InMemoryHierarchyCache
) : ByteCodeIndexer {

    override fun index(classNode: ClassNode) {
        val clazzSymbolId = persistence.findSymbolId(classNode.name.className) ?: return
        val superName = classNode.superName
        val superclasses = when {
            superName != null && superName != objectJvmName -> classNode.interfaces + superName
            else -> classNode.interfaces
        }
        superclasses.mapNotNull { persistence.findSymbolId(it.className) }
            .forEach {
                hierarchy.getOrPut(it) { ConcurrentHashMap() }
                    .getOrPut(location.id) { ConcurrentHashMap.newKeySet() }
                    .add(clazzSymbolId)
            }
    }

    override fun flush(jooq: DSLContext) {
    }
}

data class InMemoryHierarchyReq(val name: String, val allHierarchy: Boolean = true, val full: Boolean = false)

object InMemoryHierarchy : JIRFeature<InMemoryHierarchyReq, ClassSource> {

    private val hierarchies = ConcurrentHashMap<JIRDatabase, InMemoryHierarchyCache>()

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                signal.jIRdb.persistence.read { jooq ->
                    val cache = InMemoryHierarchyCache().also {
                        hierarchies[signal.jIRdb] = it
                    }
                    jooq.select(CLASSES.NAME, CLASSHIERARCHIES.SUPER_ID, CLASSES.LOCATION_ID)
                        .from(CLASSHIERARCHIES)
                        .join(CLASSES).on(CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID))
                        .fetch().forEach { (classSymbolId, superClassId, locationId) ->
                            cache.getOrPut(superClassId!!) { ConcurrentHashMap() }
                                .getOrPut(locationId!!) { ConcurrentHashMap.newKeySet() }
                                .add(classSymbolId!!)
                        }
                }
            }

            is JIRSignal.LocationRemoved -> {
                signal.jIRdb.persistence.write {
                    val id = signal.location.id
                    hierarchies[signal.jIRdb]?.values?.forEach {
                        it.remove(id)
                    }
                }
            }

            is JIRSignal.Drop -> {
                hierarchies[signal.jIRdb]?.clear()
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JIRClasspath, req: InMemoryHierarchyReq): Sequence<ClassSource> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JIRClasspath, req: InMemoryHierarchyReq): Sequence<ClassSource> {
        val persistence = classpath.db.persistence
        val locationIds = classpath.registeredLocations.map { it.id }
        if (req.name == "java.lang.Object") {
            return BatchedSequence(50) { offset, batchSize ->
                persistence.read { jooq ->
                    val whereCondition = if (offset == null) {
                        CLASSES.LOCATION_ID.`in`(locationIds)
                    } else {
                        CLASSES.LOCATION_ID.`in`(locationIds).and(
                            CLASSES.ID.greaterThan(offset)
                        )
                    }

                    jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                        .from(CLASSES)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .where(whereCondition)
                        .orderBy(CLASSES.ID)
                        .limit(batchSize)
                        .fetch()
                        .mapNotNull { (classId, className, locationId) ->
                            classId!! to PersistenceClassSource(
                                classpath = classpath,
                                classId = classId,
                                className = className!!,
                                locationId = locationId!!
                            )
                        }
                }
            }
        }
        val hierarchy = hierarchies[classpath.db] ?: return emptySequence()

        fun getSubclasses(
            symbolId: Long,
            locationIds: Set<Long>,
            transitive: Boolean,
            result: HashSet<Long>
        ) {
            val subclasses = hierarchy[symbolId]?.entries?.flatMap {
                when {
                    locationIds.contains(it.key) -> it.value
                    else -> emptyList()
                }
            }.orEmpty().toSet()
            result.addAll(subclasses)
            if (transitive) {
                subclasses.forEach {
                    getSubclasses(it, locationIds, true, result)
                }
            }

        }

        val classSymbol = persistence.findSymbolId(req.name) ?: return emptySequence()

        val allSubclasses = hashSetOf<Long>()
        getSubclasses(classSymbol, locationIds.toSet(), req.allHierarchy, allSubclasses)
        if (allSubclasses.isEmpty()) {
            return emptySequence()
        }
        val allIds = allSubclasses.toList()
        return BatchedSequence<ClassSource>(50) { offset, batchSize ->
            persistence.read { jooq ->
                val index = offset ?: 0
                val ids = allIds.subList(index.toInt(), min(allIds.size, index.toInt() + batchSize))
                if (ids.isEmpty()) {
                    emptyList()
                } else {
                    jooq.select(
                        SYMBOLS.NAME, CLASSES.ID, CLASSES.LOCATION_ID, when {
                            req.full -> CLASSES.BYTECODE
                            else -> DSL.inline(ByteArray(0)).`as`(CLASSES.BYTECODE)
                        }
                    ).from(CLASSES)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .where(SYMBOLS.ID.`in`(ids).and(CLASSES.LOCATION_ID.`in`(locationIds)))
                        .fetch()
                        .mapNotNull { (className, classId, locationId, byteCode) ->
                            val source = PersistenceClassSource(
                                classpath = classpath,
                                classId = classId!!,
                                className = className!!,
                                locationId = locationId!!
                            ).let {
                                it.bind(byteCode.takeIf { req.full })
                            }
                            (batchSize + index) to source
                        }
                }
            }
        }
    }

    override fun newIndexer(jIRdb: JIRDatabase, location: RegisteredLocation): ByteCodeIndexer {
        return InMemoryHierarchyIndexer(jIRdb.persistence, location, hierarchies.getOrPut(jIRdb) { ConcurrentHashMap() })
    }

}

internal fun JIRClasspath.findSubclassesInMemory(
    name: String,
    allHierarchy: Boolean,
    full: Boolean
): Sequence<JIRClassOrInterface> {
    return InMemoryHierarchy.syncQuery(this, InMemoryHierarchyReq(name, allHierarchy, full)).map {
        toJIRClass(it)
    }
}