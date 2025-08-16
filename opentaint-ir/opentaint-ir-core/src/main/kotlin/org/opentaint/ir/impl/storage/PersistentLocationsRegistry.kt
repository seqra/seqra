package org.opentaint.ir.impl.storage

import org.opentaint.ir.api.jvm.JIRByteCodeLocation
import org.opentaint.ir.api.jvm.LocationType
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.storage.StorageContext
import org.opentaint.ir.api.storage.ers.getEntityOrNull
import org.opentaint.ir.impl.CleanupResult
import org.opentaint.ir.impl.JIRDatabaseImpl
import org.opentaint.ir.impl.JIRInternalSignal
import org.opentaint.ir.impl.LocationsRegistry
import org.opentaint.ir.impl.LocationsRegistrySnapshot
import org.opentaint.ir.impl.RefreshResult
import org.opentaint.ir.impl.RegistrationResult
import org.opentaint.ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap.KeySetView
import java.util.concurrent.atomic.AtomicLong

class PersistentLocationsRegistry(private val jIRdb: JIRDatabaseImpl) : LocationsRegistry {

    private val persistence = jIRdb.persistence

    // non-null only for SQL-based persistence
    private val idGen: AtomicLong? = persistence.read { context ->
        context.execute(
            sqlAction = { jooq -> AtomicLong(BYTECODELOCATIONS.ID.maxId(jooq) ?: 0) },
            noSqlAction = { null }
        )
    }

    init {
        persistence.write { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.update(BYTECODELOCATIONS)
                        .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                        .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                        .execute()
                },
                noSqlAction = { txn ->
                    txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                        .filter { it.get<LocationState>(BytecodeLocationEntity.STATE) != LocationState.PROCESSED }
                        .forEach { it[BytecodeLocationEntity.STATE] = LocationState.OUTDATED.ordinal }
                }
            )
        }
    }

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS).fetch { record ->
                        PersistentByteCodeLocation(
                            jIRdb,
                            PersistentByteCodeLocationData.fromSqlRecord(record)
                        )
                    }
                },
                noSqlAction = { txn ->
                    txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE).map { entity ->
                        PersistentByteCodeLocation(
                            jIRdb,
                            PersistentByteCodeLocationData.fromErsEntity(entity)
                        )
                    }.toList()
                }
            )
        }

    private val notRuntimeLocations: List<PersistentByteCodeLocation>
        get() = persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.RUNTIME.ne(true)).fetch { record ->
                        PersistentByteCodeLocation(
                            jIRdb,
                            PersistentByteCodeLocationData.fromSqlRecord(record)
                        )
                    }
                },
                noSqlAction = { txn ->
                    txn.find(
                        type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                        propertyName = BytecodeLocationEntity.IS_RUNTIME,
                        value = false
                    ).map { entity ->
                        PersistentByteCodeLocation(
                            jIRdb,
                            PersistentByteCodeLocationData.fromErsEntity(entity)
                        )
                    }.toList()
                }
            )
        }

    override lateinit var runtimeLocations: List<RegisteredLocation>

    override val snapshots: KeySetView<LocationsRegistrySnapshot, Boolean> = ConcurrentHashMap.newKeySet()

    private fun StorageContext.save(location: JIRByteCodeLocation) =
        PersistentByteCodeLocation(jIRdb, location.findOrNew(this), location)

    override fun setup(runtimeLocations: List<JIRByteCodeLocation>): RegistrationResult {
        return registerIfNeeded(runtimeLocations).also {
            this.runtimeLocations = it.registered
        }
    }
//
//    fun restorePure() {
//        runtimeLocations = persistence.read {
//            it.selectFrom(BYTECODELOCATIONS)
//                .where(BYTECODELOCATIONS.RUNTIME.eq(true))
//                .fetch {
//                    PersistentByteCodeLocation(jIRdb, it.id!!)
//                }
//        }
//    }

    override fun afterProcessing(locations: List<RegisteredLocation>) {
        val ids = locations.map { it.id }
        persistence.write { context ->
            context.execute(
                sqlAction = { jooq ->
                    jooq.update(BYTECODELOCATIONS)
                        .set(BYTECODELOCATIONS.STATE, LocationState.PROCESSED.ordinal)
                        .where(BYTECODELOCATIONS.ID.`in`(ids))
                        .execute()
                },
                noSqlAction = { txn ->
                    ids.forEach { id ->
                        val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)
                        entity?.set(BytecodeLocationEntity.STATE, LocationState.PROCESSED.ordinal)
                    }
                }
            )
        }
        jIRdb.featuresRegistry.broadcast(JIRInternalSignal.AfterIndexing)
    }

    override fun registerIfNeeded(locations: List<JIRByteCodeLocation>): RegistrationResult {
        val uniqueLocations = locations.toSet()
        return persistence.write { context ->
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JIRByteCodeLocation>()
            val fsIds = uniqueLocations.map { it.fileSystemId }
            val existing = context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.UNIQUEID.`in`(fsIds)).map { record ->
                        PersistentByteCodeLocationData.fromSqlRecord(record)
                    }
                },
                noSqlAction = { txn ->
                    fsIds.flatMap { fsId ->
                        txn.find(
                            type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                            propertyName = BytecodeLocationEntity.FILE_SYSTEM_ID,
                            value = fsId
                        )
                    }.map { entity -> PersistentByteCodeLocationData.fromErsEntity(entity) }
                }
            ).associateBy { it.fileSystemId }

            uniqueLocations.forEach {
                val found = existing[it.fileSystemId]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(jIRdb, found, it)
                }
            }
            val records = context.execute(
                sqlAction = { jooq ->
                    val records = toAdd.map { add ->
                        idGen!!.incrementAndGet() to add
                    }
                    jooq.connection {
                        it.insertElements(BYTECODELOCATIONS, records) { (id, location) ->
                            setLong(1, id)
                            setString(2, location.path)
                            setString(3, location.fileSystemId)
                            setBoolean(4, location.type == LocationType.RUNTIME)
                            setInt(5, LocationState.INITIAL.ordinal)
                            setNull(6, Types.BIGINT)
                        }
                    }
                    records
                },
                noSqlAction = { txn ->
                    toAdd.map { location ->
                        val entity = txn.newEntity(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                        entity[BytecodeLocationEntity.PATH] = location.path
                        entity[BytecodeLocationEntity.FILE_SYSTEM_ID] = location.fileSystemId
                        entity[BytecodeLocationEntity.IS_RUNTIME] = location.type == LocationType.RUNTIME
                        entity[BytecodeLocationEntity.STATE] = LocationState.INITIAL.ordinal
                        entity.id.instanceId to location
                    }
                }
            )
            val added = records.map {
                PersistentByteCodeLocation(
                    jIRdb.persistence,
                    jIRdb.runtimeVersion,
                    it.first,
                    null,
                    it.second
                )
            }
            RegistrationResult(result + added, added)
        }
    }

    private fun StorageContext.deprecate(locations: List<RegisteredLocation>) {
        locations.forEach {
            jIRdb.featuresRegistry.broadcast(JIRInternalSignal.LocationRemoved(it))
        }
        val locationIds = locations.map { it.id }.toSet()
        execute(
            sqlAction = { jooq ->
                jooq.deleteFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.ID.`in`(locationIds)).execute()
            },
            noSqlAction = { txn ->
                txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                    .filter { it.id.instanceId in locationIds }
                    .forEach { it.delete() }
            }
        )

    }

    override fun refresh(): RefreshResult {
        val deprecated = arrayListOf<PersistentByteCodeLocation>()
        val newLocations = arrayListOf<JIRByteCodeLocation>()
        val updated = hashMapOf<JIRByteCodeLocation, PersistentByteCodeLocation>()
        notRuntimeLocations.forEach { location ->
            val jIRLocation = location.jIRLocation
            when {
                jIRLocation == null -> {
                    if (!location.hasReferences(snapshots)) {
                        deprecated.add(location)
                    }
                }

                jIRLocation.isChanged() -> {
                    val refreshed = jIRLocation.createRefreshed()
                    if (refreshed != null) {
                        newLocations.add(refreshed)
                    }
                    if (!location.hasReferences(snapshots)) {
                        deprecated.add(location)
                    } else {
                        updated[jIRLocation] = location
                    }
                }
            }
        }
        val new = persistence.write { context ->
            context.deprecate(deprecated)
            newLocations.map { location ->
                val refreshed = context.save(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    context.execute(
                        sqlAction = { jooq ->
                            jooq.update(BYTECODELOCATIONS)
                                .set(BYTECODELOCATIONS.UPDATED_ID, refreshed.id)
                                .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                                .where(BYTECODELOCATIONS.ID.eq(toUpdate.id)).execute()
                        },
                        noSqlAction = { txn ->
                            txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, toUpdate.id)
                                ?.let {
                                    it.addLink(
                                        BytecodeLocationEntity.UPDATED_LINK,
                                        txn.getEntityOrNull(
                                            BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                                            refreshed.id
                                        )!!
                                    )
                                    it[BytecodeLocationEntity.STATE] = LocationState.OUTDATED.ordinal
                                }
                        }
                    )
                }
                refreshed
            }
        }
        return RefreshResult(new = new)
    }

    override fun newSnapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot {
        return LocationsRegistrySnapshot(this, classpathSetLocations).also {
            snapshots.add(it)
        }
    }

    override fun cleanup(): CleanupResult {
        return persistence.write { context ->
            val deprecated = context.execute(
                sqlAction = { jooq ->
                    jooq.selectFrom(BYTECODELOCATIONS)
                        .where(BYTECODELOCATIONS.UPDATED_ID.isNotNull).fetch()
                        .map { PersistentByteCodeLocationData.fromSqlRecord(it) }
                },
                noSqlAction = { txn ->
                    txn.all(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                        .filter { it.getLinks(BytecodeLocationEntity.UPDATED_LINK).isNotEmpty }
                        .map { PersistentByteCodeLocationData.fromErsEntity(it) }.toList()
                }
            )
                .filterNot { data -> snapshots.any { it.ids.contains(data.id) } }
                .map { PersistentByteCodeLocation(jIRdb, it) }
            context.deprecate(deprecated)
            CleanupResult(deprecated)
        }
    }

    override fun close(snapshot: LocationsRegistrySnapshot) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
        jIRdb.featuresRegistry.broadcast(JIRInternalSignal.Closed)
        runtimeLocations = emptyList()
    }

    private fun JIRByteCodeLocation.findOrNew(context: StorageContext): PersistentByteCodeLocationData {
        val existing = findOrNull(context)
        if (existing != null) {
            return existing
        }
        return context.execute(
            sqlAction = { jooq ->
                val record = BytecodelocationsRecord().also {
                    it.path = path
                    it.uniqueid = fileSystemId
                    it.runtime = type == LocationType.RUNTIME
                }
                jooq.insertInto(BYTECODELOCATIONS).set(record)
                PersistentByteCodeLocationData.fromSqlRecord(record)
            },
            noSqlAction = { txn ->
                val entity =
                    txn.find(
                        type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                        propertyName = BytecodeLocationEntity.PATH,
                        value = path
                    ).firstOrNull() ?: txn.newEntity(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE)
                entity[BytecodeLocationEntity.PATH] = path
                entity[BytecodeLocationEntity.FILE_SYSTEM_ID] = fileSystemId
                entity[BytecodeLocationEntity.IS_RUNTIME] = type == LocationType.RUNTIME
                PersistentByteCodeLocationData.fromErsEntity(entity)
            }
        )
    }

    private fun JIRByteCodeLocation.findOrNull(context: StorageContext): PersistentByteCodeLocationData? {
        return context.execute(
            sqlAction = { jooq ->
                jooq.selectFrom(BYTECODELOCATIONS)
                    .where(BYTECODELOCATIONS.PATH.eq(path).and(BYTECODELOCATIONS.UNIQUEID.eq(fileSystemId)))
                    .fetchAny()
                    ?.let { PersistentByteCodeLocationData.fromSqlRecord(it) }
            },
            noSqlAction = { txn ->
                txn.find(
                    type = BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE,
                    propertyName = BytecodeLocationEntity.PATH,
                    value = path
                )
                    .firstOrNull { it.get<String>(BytecodeLocationEntity.FILE_SYSTEM_ID) == fileSystemId }
                    ?.let {
                        PersistentByteCodeLocationData.fromErsEntity(it)
                    }
            }
        )
    }

}

object BytecodeLocationEntity {
    const val BYTECODE_LOCATION_ENTITY_TYPE = "ByteCodeLocation"
    const val STATE = "state"
    const val IS_RUNTIME = "isRuntime"
    const val PATH = "path"
    const val FILE_SYSTEM_ID = "fileSystemId"
    const val UPDATED_LINK = "updatedLink"
}
