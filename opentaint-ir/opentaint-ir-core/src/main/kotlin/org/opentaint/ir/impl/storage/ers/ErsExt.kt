package org.opentaint.ir.impl.storage.ers

import org.opentaint.ir.api.jvm.ClassSource
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.impl.fs.PersistenceClassSource

fun Sequence<Entity>.toClassSourceSequence(db: JIRDatabase): Sequence<ClassSource> {
    val persistence = db.persistence
    return map { clazz ->
        val classId: Long = clazz.id.instanceId
        PersistenceClassSource(
            db = db,
            className = persistence.findSymbolName(clazz.getCompressed<Long>("nameId")!!),
            classId = classId,
            locationId = clazz.getCompressed<Long>("locationId")!!,
            cachedByteCode = persistence.findBytecode(classId)
        )
    }
}