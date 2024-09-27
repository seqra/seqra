package org.opentaint.ir.impl.storage.scheme

import jetbrains.exodus.entitystore.Entity
import mu.KLogging
import org.opentaint.ir.impl.CompilationDatabaseImpl
import org.opentaint.ir.impl.storage.PersistentEnvironment

class DatabaseEntity(internal val entity: Entity) {

    var runtimeLocation: String
        get() = entity.getProperty("runtimeLocation") as String
        set(value) {
            entity.setProperty("runtimeLocation", value)
        }

    val locations: List<LocationEntity>
        get() = entity.getLinks("locations").toList().map { LocationEntity(entity) }

    fun addLocation(locationEntity: LocationEntity) {
        entity.addLink("locations", locationEntity.entity)
    }

    fun removeLocation(locationEntity: LocationEntity) {
        entity.deleteLink("locations", locationEntity.entity)
    }

    fun removeLocations() {
        locations.forEach { it.delete() }
        entity.deleteLinks("locations")
    }
}

class DatabaseStore(private val dbStore: PersistentEnvironment) {

    companion object : KLogging() {
        const val type = "CompilationDatabase"
    }

    fun get(): DatabaseEntity {
        return dbStore.transactional {
            DatabaseEntity(getAll(type).first ?: throw IllegalStateException("no database found"))
        }
    }

    fun save(db: CompilationDatabaseImpl, clearOnStart: Boolean): DatabaseEntity {
        return dbStore.transactional {
            var existed = getAll(type).first
            if (clearOnStart && existed != null) {
                DatabaseEntity(existed).removeLocations()
                existed.delete()
                existed = null
            }
            val dbEntity = if (existed == null) {
                DatabaseEntity(newEntity(type)).also {
                    it.runtimeLocation = db.javaRuntime.javaHome.absolutePath
                }
            } else {
                DatabaseEntity(existed)
            }
            db.registry.locations.forEach {
                dbEntity.addLocation(locationEntity = dbStore.locationStore.findOrNew(it))
            }
            dbEntity
        }
    }

}