package org.opentaint.ir.impl

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.RegisteredLocation
import java.io.Closeable

interface LocationsRegistry : Closeable {
    // all locations
    val actualLocations: List<RegisteredLocation>
    val runtimeLocations: List<RegisteredLocation>

    fun cleanup(): CleanupResult
    fun refresh(): RefreshResult
    fun setup(runtimeLocations: List<JIRByteCodeLocation>): RegistrationResult

    fun registerIfNeeded(locations: List<JIRByteCodeLocation>): RegistrationResult
    fun afterProcessing(locations: List<RegisteredLocation>)

    fun newSnapshot(classpathSetLocations: List<RegisteredLocation>): LocationsRegistrySnapshot

    fun close(snapshot: LocationsRegistrySnapshot)

    fun RegisteredLocation.hasReferences(snapshots: Set<LocationsRegistrySnapshot>): Boolean {
        return snapshots.isNotEmpty() && snapshots.any { it.ids.contains(id) }
    }

}

class RegistrationResult(val registered: List<RegisteredLocation>, val new: List<RegisteredLocation>)
class RefreshResult(val new: List<RegisteredLocation>)
class CleanupResult(val outdated: List<RegisteredLocation>)

open class LocationsRegistrySnapshot(
    private val registry: LocationsRegistry,
    val locations: List<RegisteredLocation>
) : Closeable {

    val ids = locations.map { it.id }.toHashSet()

    override fun close() = registry.close(this)

}