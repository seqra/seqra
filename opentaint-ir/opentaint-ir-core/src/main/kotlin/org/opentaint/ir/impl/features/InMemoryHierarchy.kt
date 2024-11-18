package org.opentaint.ir.impl.features

import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.api.ClassSource
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRFeature
import org.opentaint.ir.api.JIRSignal
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.bytecode.JIRClassOrInterfaceImpl
import org.opentaint.ir.impl.fs.ClassSourceImpl
import org.opentaint.ir.impl.fs.className
import org.opentaint.ir.impl.storage.BatchedSequence
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.opentaint.ir.impl.vfs.LazyPersistentByteCodeLocation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

typealias InMemoryHierarchyCache = ConcurrentHashMap<Long, ConcurrentHashMap<Long, MutableSet<Long>>>

private val objectJvmName = Type.getInternalName(Any::class.java)

class FastHierarchyIndexer(
    private val persistence: JIRDBPersistence,
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

data class FastHierarchyReq(val name: String, val allHierarchy: Boolean = true)

object InMemoryHierarchy : JIRFeature<FastHierarchyReq, ClassSource> {

    private val hierarchies = ConcurrentHashMap<JIRDB, InMemoryHierarchyCache>()

    override fun onSignal(signal: JIRSignal) {
        when (signal) {
            is JIRSignal.BeforeIndexing -> {
                signal.jirdb.persistence.read { jooq ->
                    val cache = InMemoryHierarchyCache().also {
                        hierarchies[signal.jirdb] = it
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
                signal.jirdb.persistence.write {
                    val id = signal.location.id
                    hierarchies[signal.jirdb]?.values?.forEach {
                        it.remove(id)
                    }
                }
            }

            is JIRSignal.Drop -> {
                hierarchies[signal.jirdb]?.clear()
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JIRClasspath, req: FastHierarchyReq): Sequence<ClassSource> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JIRClasspath, req: FastHierarchyReq): Sequence<ClassSource> {
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

                    jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.BYTECODE, CLASSES.LOCATION_ID)
                        .from(CLASSES)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .where(whereCondition)
                        .orderBy(CLASSES.ID)
                        .limit(batchSize)
                        .fetch()
                        .mapNotNull { (classId, className, byteCode, locationId) ->
                            classId!! to ClassSourceImpl(
                                LazyPersistentByteCodeLocation(persistence, locationId!!, classpath.db.runtimeVersion),
                                className!!, byteCode!!
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
        val allHashes = allSubclasses.toList()
        return BatchedSequence<ClassSource>(50) { offset, batchSize ->
            persistence.read { jooq ->
                val index = offset?.toInt() ?: 0
                val hashes = allHashes.subList(index, min(allHashes.size, index + batchSize))
                if (hashes.isEmpty()) {
                    emptyList()
                } else {
                    jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.BYTECODE, CLASSES.LOCATION_ID)
                        .from(CLASSES)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .where(SYMBOLS.ID.`in`(hashes).and(CLASSES.LOCATION_ID.`in`(locationIds)))
                        .fetch()
                        .mapNotNull { (classId, className, byteCode, locationId) ->
                            (index.toLong() + batchSize) to ClassSourceImpl(
                                LazyPersistentByteCodeLocation(persistence, locationId!!, classpath.db.runtimeVersion),
                                className!!, byteCode!!
                            )
                        }
                }
            }
        }
    }

    override fun newIndexer(jirdb: JIRDB, location: RegisteredLocation): ByteCodeIndexer {
        return FastHierarchyIndexer(jirdb.persistence, location, hierarchies.getOrPut(jirdb) { ConcurrentHashMap() })
    }

}

class InMemoryHierarchyExtension(private val cp: JIRClasspath) {

    fun findSubClasses(name: String, allHierarchy: Boolean): Sequence<JIRClassOrInterface> {
        return InMemoryHierarchy.syncQuery(cp, FastHierarchyReq(name, allHierarchy)).map {
            JIRClassOrInterfaceImpl(cp, it)
        }
    }
}

suspend fun JIRClasspath.inMemoryHierarchyExt(): InMemoryHierarchyExtension {
    db.awaitBackgroundJobs()
    return InMemoryHierarchyExtension(this)
}

internal fun JIRClasspath.findSubclassesInMemory(name: String, allHierarchy: Boolean): Sequence<JIRClassOrInterface> {
    return InMemoryHierarchy.syncQuery(this, FastHierarchyReq(name, allHierarchy)).map {
        JIRClassOrInterfaceImpl(this, it)
    }
}