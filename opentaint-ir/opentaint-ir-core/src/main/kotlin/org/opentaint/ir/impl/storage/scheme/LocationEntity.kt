package org.opentaint.ir.impl.storage.scheme

import com.fasterxml.jackson.core.type.TypeReference
import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.StoreTransaction
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.LocationScope
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.storage.mapper
import org.opentaint.ir.impl.types.ClassInfo
import java.io.InputStream

class LocationEntity(internal val entity: Entity) {

    var id: String
        get() = entity.getProperty("id") as String
        set(value) {
            entity.setProperty("id", value)
        }

    var url: String
        get() = entity.getProperty("url") as String
        set(value) {
            entity.setProperty("url", value)
        }

    var classes: List<ClassInfo>
        get() {
            val json = entity.getProperty("classes") as String? ?: return emptyList()
            return mapper.readValue(json, object : TypeReference<List<ClassInfo>>() {})
        }
        set(value) {
            val json = mapper.writeValueAsString(value)
            entity.setProperty("classes", json)
        }

    var isRuntime: Boolean
        get() = entity.getProperty("isRuntime") as? Boolean ?: false
        set(value) {
            entity.setProperty("isRuntime", value)
        }

    fun index(key: String): InputStream? {
        return entity.getBlob(key)
    }

    fun index(key: String, input: InputStream) {
        entity.setBlob(key, input)
    }

    fun delete() {
        entity.delete()
    }
}


class LocationStore(private val dbStore: PersistentEnvironment) {

    companion object {
        const val type = "Location"
    }

    val all: Sequence<LocationEntity>
        get() {
            return dbStore.transactional {
                getAll(type).asSequence().map { LocationEntity(it) }
            }
        }

    fun findOrNew(tx: StoreTransaction, location: ByteCodeLocation): LocationEntity {
        val found = tx.find(type, LocationEntity::id.name, location.id).first
        return if (found == null) {
            LocationEntity(tx.newEntity(type)).also {
                it.id = location.id
                it.url = location.locationURL.toString()
                it.isRuntime = location.scope == LocationScope.RUNTIME
            }
        } else {
            LocationEntity(found)
        }
    }

    fun findOrNew(location: ByteCodeLocation): LocationEntity {
        return dbStore.transactional {
            val loc = findOrNew(this, location)
            dbStore.databaseStore.get().addLocation(loc)
            loc
        }
    }

    fun saveClasses(location: ByteCodeLocation, classes: List<ClassInfo>) {
        dbStore.transactional {
            val entity = findOrNew(this, location)
            entity.classes = classes
        }
    }
}