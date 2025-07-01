package org.opentaint.ir.impl.features

import org.opentaint.ir.api.jvm.*
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.storage.ers.links
import org.opentaint.ir.api.jvm.storage.ers.propertyOf
import org.opentaint.ir.impl.asSymbolId
import org.opentaint.ir.impl.fs.PersistenceClassSource
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.BatchedSequence
import org.opentaint.ir.impl.storage.defaultBatchSize
import org.opentaint.ir.impl.storage.execute
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.storage.toJIRDBContext
import org.opentaint.ir.impl.storage.withoutAutoCommit
import org.jooq.impl.DSL
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

typealias InMemoryHierarchyCache = ConcurrentHashMap<Long, ConcurrentHashMap<Long, MutableSet<Long>>>

private val objectJvmName = Type.getInternalName(Any::class.java)

class InMemoryHierarchyIndexer(
    persistence: JIRDatabasePersistence,
    private val location: RegisteredLocation,
    private val hierarchy: InMemoryHierarchyCache
) : ByteCodeIndexer {

    private val interner = persistence.symbolInterner

    override fun index(classNode: ClassNode) {
        val clazzSymbolId = classNode.name.className.asSymbolId(interner)
        val superName = classNode.superName
        val superclasses = when {
            superName != null && superName != objectJvmName -> classNode.interfaces + superName
            else -> classNode.interfaces
        }
        superclasses.map { it.className.asSymbolId(interner) }
            .forEach {
                hierarchy.getOrPut(it) { ConcurrentHashMap() }
                    .getOrPut(location.id) { ConcurrentHashMap.newKeySet() }
                    .add(clazzSymbolId)
            }
    }

    override fun flush(context: JIRDBContext) {
        context.execute(
            sqlAction = { jooq ->
                jooq.withoutAutoCommit { conn ->
                    interner.flush(toJIRDBContext(jooq, conn))
                }
            },
            noSqlAction = {
                interner.flush(context)
            }
        )
    }
}

data class InMemoryHierarchyReq(val name: String, val allHierarchy: Boolean = true, val full: Boolean = false)

object InMemoryHierarchy : JIRFeature<InMemoryHierarchyReq, ClassSource> {

    private val hierarchies = ConcurrentHashMap<JIRDatabase, InMemoryHierarchyCache>()

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                signal.jIRdb.persistence.read { context ->
                    val cache = InMemoryHierarchyCache().also {
                        hierarchies[signal.jIRdb] = it
                    }
                    val result = mutableListOf<Triple<Long?, Long?, Long?>>()
                    context.execute(
                        sqlAction = { jooq ->
                            jooq.select(CLASSES.NAME, CLASSHIERARCHIES.SUPER_ID, CLASSES.LOCATION_ID)
                                .from(CLASSHIERARCHIES)
                                .join(CLASSES).on(CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID))
                                .fetch().forEach { (classSymbolId, superClassId, locationId) ->
                                    result += (Triple(classSymbolId, superClassId, locationId))
                                }
                        },
                        noSqlAction = { txn ->
                            txn.all("Class").map { clazz ->
                                val locationId: Long? = clazz.getCompressed("locationId")
                                val classSymbolId: Long? = clazz.getCompressed("nameId")
                                val superClasses = mutableListOf<Long>()
                                clazz.getCompressed<Long>("inherits")?.let { nameId -> superClasses += nameId }
                                links(clazz, "implements").asIterable.forEach { anInterface ->
                                    anInterface.getCompressed<Long>("nameId")?.let { nameId -> superClasses += nameId }
                                }
                                superClasses.forEach { nameId ->
                                    result += (Triple(classSymbolId, nameId, locationId))
                                }
                            }
                        }
                    )
                    result.forEach { (classSymbolId, superClassId, locationId) ->
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

            is JIRSignal.Closed -> {
                hierarchies.remove(signal.jIRdb)
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JIRClasspath, req: InMemoryHierarchyReq): Sequence<ClassSource> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JIRClasspath, req: InMemoryHierarchyReq): Sequence<ClassSource> {
        val persistence = classpath.db.persistence
        if (req.name == JAVA_OBJECT) {
            return persistence.read { classpath.allClassesExceptObject(it, !req.allHierarchy) }
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
            if (subclasses.isNotEmpty()) {
                result.addAll(subclasses)
            }
            if (transitive) {
                subclasses.forEach {
                    getSubclasses(it, locationIds, true, result)
                }
            }
        }

        val locationIds = classpath.registeredLocations.mapTo(mutableSetOf()) { it.id }
        val classSymbolId = persistence.findSymbolId(req.name)

        val allSubclasses = hashSetOf<Long>()
        getSubclasses(classSymbolId, locationIds, req.allHierarchy, allSubclasses)
        if (allSubclasses.isEmpty()) {
            return emptySequence()
        }
        return persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    val allIds = allSubclasses.toList()
                    BatchedSequence<ClassSource>(defaultBatchSize) { offset, batchSize ->
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
                                        db = classpath.db,
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
                },
                noSqlAction = { txn ->
                    txn.all("Class").asSequence()
                        .filter { clazz ->
                            clazz.getCompressed<Long>("nameId") in allSubclasses && clazz.getCompressed<Long>("locationId") in locationIds
                        }
                        .map { clazz ->
                            val classId: Long = clazz.id.instanceId
                            PersistenceClassSource(
                                db = classpath.db,
                                className = persistence.findSymbolName(clazz.getCompressed<Long>("nameId")!!),
                                classId = classId,
                                locationId = clazz.getCompressed<Long>("locationId")!!,
                                cachedByteCode = if (req.full) persistence.findBytecode(classId) else null
                            )
                        }
                        // Eager evaluation is needed, because all computations must be done within current transaction,
                        // i.e. ERS can't be used outside `persistence.read { ... }`, when sequence is actually iterated
                        .toList().asSequence()
                }
            )
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
