@file:JvmName("Diagnostics")

package org.opentaint.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.impl.DSL

/**
 * finds out duplicates classes
 *
 * @return map with name and count of classes
 */
suspend fun JIRClasspath.duplicatedClasses(): Map<String, Int> {
    db.awaitBackgroundJobs()
    return db.persistence.read {
        it.select(SYMBOLS.NAME, DSL.count(SYMBOLS.NAME)).from(CLASSES)
            .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
            .where(CLASSES.LOCATION_ID.`in`(registeredLocations.map { it.id }))
            .groupBy(SYMBOLS.NAME)
            .having(DSL.count(SYMBOLS.NAME).greaterThan(1))
            .fetch()
            .map { (name, count) -> name!! to count!!}
            .toMap()
    }

}

fun JIRClasspath.asyncDuplicatedClasses() = GlobalScope.future { duplicatedClasses() }
