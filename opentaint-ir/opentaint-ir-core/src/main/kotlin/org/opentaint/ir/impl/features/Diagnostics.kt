@file:JvmName("Diagnostics")

package org.opentaint.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.impl.storage.execute
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
    val persistence = db.persistence
    return persistence.read { context ->
        context.execute(
            sqlAction = { jooq ->
                jooq.select(SYMBOLS.NAME, DSL.count(SYMBOLS.NAME)).from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(CLASSES.LOCATION_ID.`in`(registeredLocations.map { it.id }))
                    .groupBy(SYMBOLS.NAME)
                    .having(DSL.count(SYMBOLS.NAME).greaterThan(1))
                    .fetch()
                    .map { (name, count) -> name!! to count!! }
                    .toMap()
            },
            noSqlAction = { txn ->
                val result = mutableMapOf<String, Int>().also { result ->
                    txn.all("Class").forEach { clazz ->
                        val className = persistence.findSymbolName(clazz.getCompressed<Long>("nameId")!!)
                        result[className] = result.getOrDefault(className, 0) + 1
                    }
                }
                result.filterKeys { className -> result[className]!! > 1 }
            }
        )
    }
}

fun JIRClasspath.asyncDuplicatedClasses() = GlobalScope.future { duplicatedClasses() }
