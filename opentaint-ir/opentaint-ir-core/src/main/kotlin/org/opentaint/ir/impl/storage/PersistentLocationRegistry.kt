package org.opentaint.ir.impl.storage

import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.LocationType
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.impl.FeaturesRegistry
import org.opentaint.ir.impl.LocationsRegistry
import org.opentaint.ir.impl.LocationsRegistrySnapshot
import org.opentaint.ir.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.opentaint.ir.impl.vfs.PersistentByteCodeLocation
import org.opentaint.ir.impl.vfs.toJcLocation

class PersistentLocationRegistry(
    private val persistence: SQLitePersistenceImpl,
    private val featuresRegistry: FeaturesRegistry
) : LocationsRegistry {

    override val locations: Set<JIRByteCodeLocation>
        get() = transaction(persistence.db) {
            BytecodeLocationEntity.all().toList().map { it.toJcLocation() }.toSet()
        }

    private val registeredLocations: Set<PersistentByteCodeLocation>
        get() = transaction(persistence.db) {
            BytecodeLocationEntity.all().toList().map { PersistentByteCodeLocation(it) }.toSet()
        }

    // all snapshot associated with classpaths
    internal val snapshots = HashSet<LocationsRegistrySnapshot>()

    private fun add(location: JIRByteCodeLocation): PersistentByteCodeLocation {
        return persistence.write {
            PersistentByteCodeLocation(location.findOrNew(), location)
        }
    }

    override fun addLocation(location: JIRByteCodeLocation) = add(location)

    override fun addLocations(location: List<JIRByteCodeLocation>): List<RegisteredLocation> {
        return persistence.write {
            val result = arrayListOf<RegisteredLocation>()
            val toAdd = arrayListOf<JIRByteCodeLocation>()
            locations.forEach {
                val found = BytecodeLocationEntity.find {
                    (BytecodeLocations.path eq it.path) and (BytecodeLocations.hash eq it.hash)
                }.firstOrNull()
                if (found == null) {
                    toAdd += it
                } else {
                    result += PersistentByteCodeLocation(found, it)
                }
            }
            BytecodeLocations.batchInsert(toAdd, shouldReturnGeneratedValues = false) {
                this[BytecodeLocations.hash] = it.hash
                this[BytecodeLocations.path] = it.path
                this[BytecodeLocations.runtime] = it.type == LocationType.RUNTIME
            }
            result
        }
    }

    private suspend fun refresh(location: RegisteredLocation, onRefresh: suspend (RegisteredLocation) -> Unit) {
        val jirLocation = location.jirLocation
        if (jirLocation.isChanged()) {
            val refreshedLocation = persistence.write {
                val refreshedLocation = jirLocation.createRefreshed()
                val refreshed = add(refreshedLocation)
                // let's check snapshots
                val hasReferences = location.hasReferences(snapshots)
                val entity = BytecodeLocationEntity.findById(location.id)
                    ?: throw IllegalStateException("location with ${location.id} not found")
                if (!hasReferences) {
                    featuresRegistry.onLocationRemove(location)
                    entity.delete()
                } else {
                    entity.updated = refreshed.entity
                }
                refreshedLocation
            }
            onRefresh(addLocation(refreshedLocation))
        }
    }

    override suspend fun refresh(onRefresh: suspend (RegisteredLocation) -> Unit) {
        registeredLocations.forEach {
            refresh(it, onRefresh)
        }
    }

    override fun snapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot {
        return synchronized(this) {
            LocationsRegistrySnapshot(this, classpathSetLocations).also {
                snapshots.add(it)
            }
        }
    }

    override fun cleanup(): Set<RegisteredLocation> {
        return persistence.write {
            BytecodeLocationEntity
                .find(BytecodeLocations.updated neq null)
                .toList()
                .filter { entity -> snapshots.any { it.ids.contains(entity.id.value) } }
                .map { PersistentByteCodeLocation(it) }
                .toSet()
        }
    }

    override fun onClose(snapshot: LocationsRegistrySnapshot): Set<RegisteredLocation> {
        snapshots.remove(snapshot)
        return cleanup()
    }

    override fun close() {
        // do nothing
    }
}