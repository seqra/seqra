package org.opentaint.java.compilation.database.impl

import kotlinx.collections.immutable.toImmutableList
import org.opentaint.java.compilation.database.api.ByteCodeLocation
import java.io.Closeable

class LocationsRegistry {

    // all loaded locations
    internal val locations = HashSet<ByteCodeLocation>()

    // loaded but outdated locations. that means that they are used in some snapshots but outdated
    internal val usedButOutdated = HashSet<ByteCodeLocation>()

    // all snapshot associated with classpaths
    internal val snapshots = HashSet<LocationsRegistrySnapshot>()

    fun addLocation(location: ByteCodeLocation) = synchronized(this) {
        locations.add(location)
    }

    suspend fun refresh(location: ByteCodeLocation, onRefresh: suspend (ByteCodeLocation) -> Unit) {
        if (location.isChanged()) {
            val refreshedLocation = synchronized(this) {
                val refreshedLocation = location.refreshed()
                // let's check snapshots
                val hasReferences = location.hasReferences()
                if (!hasReferences) {
                    locations.remove(location)
                } else {
                    usedButOutdated.add(location)
                }
                refreshedLocation
            }
            onRefresh(refreshedLocation)
        }
    }

    suspend fun refresh(onRefresh: suspend (ByteCodeLocation) -> Unit) {
        val currentState = synchronized(this) {
            locations.toImmutableList()
        }
        currentState.forEach { refresh(it, onRefresh) }
    }

    fun snapshot(classpathSetLocations: List<ByteCodeLocation>): LocationsRegistrySnapshot = synchronized(this) {
        classpathSetLocations.forEach { addLocation(it) }
        val snapshot = LocationsRegistrySnapshot(this, classpathSetLocations)
        snapshots.add(snapshot)
        return snapshot
    }

    fun cleanup(): Set<ByteCodeLocation> {
        synchronized(this) {
            val forRemoval = hashSetOf<ByteCodeLocation>()
            usedButOutdated.forEach {
                if (!it.hasReferences()) {
                    locations.remove(it)
                }
            }
            usedButOutdated.removeAll(forRemoval)
            locations.removeAll(forRemoval)
            return forRemoval
        }
    }

    fun onClose(snapshot: LocationsRegistrySnapshot) = synchronized(this) {
        snapshots.remove(snapshot)
        cleanup()
    }

    private fun ByteCodeLocation.hasReferences(): Boolean {
        return snapshots.isNotEmpty() && snapshots.any { it.locations.contains(this) }
    }
}

class LocationsRegistrySnapshot(
    private val registry: LocationsRegistry,
    val locations: List<ByteCodeLocation>
) : Closeable {

    override fun close() {
        registry.onClose(this)
    }
}