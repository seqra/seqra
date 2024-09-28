package org.opentaint.ir.impl.storage.scheme

import jetbrains.exodus.entitystore.Entity
import jetbrains.exodus.entitystore.StoreTransaction
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.LocationScope
import org.opentaint.ir.impl.storage.PersistentEnvironment
import org.opentaint.ir.impl.types.ClassInfo
import org.opentaint.ir.impl.types.LocationClasses
import java.io.InputStream

class LocationEntity(internal val entity: Entity) {

    companion object {
        private const val CLASSES = "classes"
        private const val URL = "url"
        private const val ID = "id"
        private const val IS_RUNTIME = "isRuntime"

    }

    var id: String
        get() = entity.getProperty(ID) as String
        set(value) {
            entity.setProperty(ID, value)
        }

    var url: String
        get() = entity.getProperty(URL) as String
        set(value) {
            entity.setProperty(URL, value)
        }

    var classes: List<ClassInfo>
        get() {
            val input = entity.getBlob(CLASSES) ?: return emptyList()
            return Cbor.decodeFromByteArray<LocationClasses>(input.readBytes()).classes
        }
        set(value) {
            val binary = Cbor.encodeToByteArray(LocationClasses(value))
            entity.setBlob(CLASSES, binary.inputStream())
        }

    var isRuntime: Boolean
        get() = entity.getProperty(IS_RUNTIME) as? Boolean ?: false
        set(value) {
            entity.setProperty(IS_RUNTIME, value)
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
                dbStore.databaseStore.get(tx).addLocation(it)
            }
        } else {
            LocationEntity(found)
        }
    }

    fun findOrNew(location: ByteCodeLocation): LocationEntity {
        return dbStore.transactional {
            val loc = findOrNew(this, location)
            dbStore.databaseStore.get(this).addLocation(loc)
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