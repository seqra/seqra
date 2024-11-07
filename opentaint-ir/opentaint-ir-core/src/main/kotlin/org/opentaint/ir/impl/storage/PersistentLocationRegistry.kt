package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import org.opentaint.ir.api.JIRDBPersistence
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.CleanupResult
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.JIRInternalSignal
import org.opentaint.ir.impl.LocationsRegistry
import org.opentaint.ir.impl.LocationsRegistrySnapshot
import org.opentaint.ir.impl.RefreshResult
import org.opentaint.ir.impl.RegistrationResult
import org.opentaint.ir.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.opentaint.ir.impl.vfs.PersistentByteCodeLocation

class PersistentLocationRegistry(
    private val persistence: JIRDBPersistence,
    private val featuresRegistry: FeaturesRegistry
) : LocationsRegistry {

    // all snapshot associated with classpaths
    internal val snapshots = HashSet<LocationsRegistrySnapshot>()

    override val actualLocations: List<PersistentByteCodeLocation>
        get() = persistence.read {
            BytecodeLocationEntity.all().toList().map { PersistentByteCodeLocation(it) }
        }

    override lateinit var runtimeLocations: List<RegisteredLocation>

    private fun add(location: JIRByteCodeLocation) = PersistentByteCodeLocation(location.findOrNew(), location)

    override fun setup(runtimeLocations: List<JIRByteCodeLocation>): RegistrationResult {
        return registerIfNeeded(runtimeLocations).also {
            this.runtimeLocations = it.registered
        }
    }

    override fun afterProcessing(locations: List<RegisteredLocation>) {
        val ids = locations.map { it.id }
        persistence.write {
            BytecodeLocations.update({ BytecodeLocations.id inList ids }) {
                it[state] = LocationState.PROCESSED
            }
        }
        featuresRegistry.broadcast(JIRInternalSignal.AfterIndexing)
    }

    override fun registerIfNeeded(locations: List<JIRByteCodeLocation>): RegistrationResult {
        return persistence.write {
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JIRByteCodeLocation>()
            val hashes = locations.map { it.hash }
            val existed = BytecodeLocationEntity.find {
                BytecodeLocations.hash inList hashes and (BytecodeLocations.state neq LocationState.INITIAL)
            }.associateBy { it.hash }

            locations.forEach {
                val found = existed[it.hash]
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(found, it)
                }
            }
            val added = toAdd.map {
                PersistentByteCodeLocation(
                    BytecodeLocationEntity.new {
                        hash = it.hash
                        path = it.path
                        runtime = it.type == LocationType.RUNTIME
                    },
                    it
                )
            }
            RegistrationResult(result + added, added)
        }
    }

    private fun deprecate(locations: List<RegisteredLocation>) {
        locations.forEach {
            featuresRegistry.broadcast(JIRInternalSignal.LocationRemoved(it))
        }
        BytecodeLocations.deleteWhere { BytecodeLocations.id inList locations.map { it.id } }
    }

    override fun refresh(): RefreshResult {
        val deprecated = arrayListOf<PersistentByteCodeLocation>()
        val newLocations = arrayListOf<JIRByteCodeLocation>()
        val updated = hashMapOf<JIRByteCodeLocation, PersistentByteCodeLocation>()
        actualLocations.forEach { location ->
            val jirLocation = location.jirLocation
            if (jirLocation.isChanged()) {
                val refreshed = jirLocation.createRefreshed()
                if (refreshed != null) {
                    newLocations.add(refreshed)
                }
                if (!location.hasReferences(snapshots)) {
                    deprecated.add(location)
                } else {
                    updated[jirLocation] = location
                }
            }
        }
        val new = persistence.write {
            deprecate(deprecated)
            newLocations.map { location ->
                val refreshed = add(location)
                val toUpdate = updated[location]
                if (toUpdate != null) {
                    toUpdate.entity.updated = refreshed.entity
                }
                refreshed
            }
        }
        return RefreshResult(new = new)
    }

    override fun newSnapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot {
        return synchronized(this) {
            LocationsRegistrySnapshot(this, classpathSetLocations).also {
                snapshots.add(it)
            }
        }
    }

    override fun cleanup(): CleanupResult {
        return persistence.write {
            val deprecated = BytecodeLocationEntity
                .find(BytecodeLocations.updated neq null)
                .toList()
                .filterNot { entity -> snapshots.any { it.ids.contains(entity.id.value) } }
                .map { PersistentByteCodeLocation(it) }
            deprecate(deprecated)
            CleanupResult(deprecated)
        }
    }

    override fun close(snapshot: LocationsRegistrySnapshot) {
        snapshots.remove(snapshot)
        cleanup()
    }

    override fun close() {
        // do nothing
    }
}