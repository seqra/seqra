package org.opentaint.opentaint-ir.impl.storage

import org.jooq.DSLContext
import org.opentaint.opentaint-ir.api.JIRByteCodeLocation
import org.opentaint.opentaint-ir.api.JIRDatabase
import org.opentaint.opentaint-ir.api.LocationType
import org.opentaint.opentaint-ir.api.RegisteredLocation
import org.opentaint.opentaint-ir.impl.CleanupResult
import org.opentaint.opentaint-ir.impl.FeaturesRegistry
import org.opentaint.opentaint-ir.impl.JIRInternalSignal
import org.opentaint.opentaint-ir.impl.LocationsRegistry
import org.opentaint.opentaint-ir.impl.LocationsRegistrySnapshot
import org.opentaint.opentaint-ir.impl.RefreshResult
import org.opentaint.opentaint-ir.impl.RegistrationResult
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.opentaint.opentaint-ir.impl.vfs.PersistentByteCodeLocation
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PersistentLocationRegistry(private val jIRdb: JIRDatabase, private val featuresRegistry: FeaturesRegistry) :
    LocationsRegistry {

    private val persistence = jIRdb.persistence

    private val idGen: AtomicLong = AtomicLong(persistence.read { BYTECODELOCATIONS.ID.maxId(it) } ?: 0)

    // all snapshot associated with classpaths
    internal val snapshots = ConcurrentHashMap.newKeySet<LocationsRegistrySnapshot>()

    init {
        persistence.write { jooq ->
            jooq.deleteFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                .execute()
        }
    }

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read {
            it.selectFrom(BYTECODELOCATIONS).fetch {
                PersistentByteCodeLocation(jIRdb, it)
            }
        }

    private val notRuntimeLocations: List<PersistentByteCodeLocation>
        get() = persistence.read {
            it.selectFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.RUNTIME.ne(true)).fetch {
                PersistentByteCodeLocation(jIRdb, it)
            }
        }

    override lateinit var runtimeLocations: List<RegisteredLocation>

    private fun DSLContext.add(location: JIRByteCodeLocation) =
        PersistentByteCodeLocation(jIRdb, location.findOrNew(this), location)

    override fun setup(runtimeLocations: List<JIRByteCodeLocation>): RegistrationResult {
        return registerIfNeeded(runtimeLocations).also {
            this.runtimeLocations = it.registered
        }
    }

    override fun afterProcessing(locations: List<RegisteredLocation>) {
        val ids = locations.map { it.id }
        persistence.write {
            it.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.PROCESSED.ordinal).where(BYTECODELOCATIONS.ID.`in`(ids))
                .execute()
        }
        featuresRegistry.broadcast(JIRInternalSignal.AfterIndexing)
    }

    override fun registerIfNeeded(locations: List<JIRByteCodeLocation>): RegistrationResult {
        return persistence.write {
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JIRByteCodeLocation>()
            val fsId = locations.map { it.fsId }
            val existed = it.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.UNIQUEID.`in`(fsId))
                .fetch().associateBy { it.uniqueid }

            locations.forEach {
                val found = existed[it.fsId]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(jIRdb, found, it)
                }
            }
            val records = toAdd.map { add ->
                idGen.incrementAndGet() to add
            }
            it.connection {
                it.insertElements(BYTECODELOCATIONS, records) {
                    val (id, location) = it
                    setLong(1, id)
                    setString(2, location.path)
                    setString(3, location.fsId)
                    setBoolean(4, location.type == LocationType.RUNTIME)
                    setInt(5, LocationState.INITIAL.ordinal)
                    setNull(6, Types.BIGINT)
                }
            }
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

    private fun DSLContext.deprecate(locations: List<RegisteredLocation>) {
        locations.forEach {
            featuresRegistry.broadcast(JIRInternalSignal.LocationRemoved(it))
        }
        deleteFrom(BYTECODELOCATIONS).where(BYTECODELOCATIONS.ID.`in`(locations.map { it.id })).execute()
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
        val new = persistence.write {
            it.deprecate(deprecated)
            newLocations.map { location ->
                val refreshed = it.add(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    it.update(BYTECODELOCATIONS)
                        .set(BYTECODELOCATIONS.UPDATED_ID, refreshed.id)
                        .set(BYTECODELOCATIONS.STATE, LocationState.OUTDATED.ordinal)
                        .where(BYTECODELOCATIONS.ID.eq(toUpdate.id)).execute()
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
        return persistence.write {
            val deprecated = it.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.UPDATED_ID.isNotNull).fetch()
                .toList()
                .filterNot { entity -> snapshots.any { it.ids.contains(entity.id) } }
                .map { PersistentByteCodeLocation(jIRdb, it) }
            it.deprecate(deprecated)
            CleanupResult(deprecated)
        }
    }

    override fun close(snapshot: LocationsRegistrySnapshot) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
        featuresRegistry.broadcast(JIRInternalSignal.Closed)
        runtimeLocations = emptyList()
    }

    private fun JIRByteCodeLocation.findOrNew(dslContext: DSLContext): BytecodelocationsRecord {
        val existed = findOrNull(dslContext)
        if (existed != null) {
            return existed
        }
        val record = BytecodelocationsRecord().also {
            it.path = path
            it.uniqueid = fsId
            it.runtime = type == LocationType.RUNTIME
        }
        record.insert()
        return record
    }

    private fun JIRByteCodeLocation.findOrNull(dslContext: DSLContext): BytecodelocationsRecord? {
        return dslContext.selectFrom(BYTECODELOCATIONS)
            .where(BYTECODELOCATIONS.PATH.eq(path).and(BYTECODELOCATIONS.UNIQUEID.eq(fsId))).fetchAny()
    }

}