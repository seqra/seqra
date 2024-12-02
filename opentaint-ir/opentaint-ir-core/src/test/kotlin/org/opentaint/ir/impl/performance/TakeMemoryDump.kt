package org.opentaint.opentaint-ir.impl.performance

import kotlinx.coroutines.runBlocking
import org.opentaint.opentaint-ir.impl.PredefinedPersistenceType
import org.opentaint.opentaint-ir.impl.allClasspath
import org.opentaint.opentaint-ir.impl.features.Builders
import org.opentaint.opentaint-ir.impl.features.InMemoryHierarchy
import org.opentaint.opentaint-ir.impl.features.Usages
import org.opentaint.opentaint-ir.impl.opentaint-ir
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.FIELDS
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.opentaint.opentaint-ir.impl.storage.jooq.tables.references.METHODS

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = opentaint-ir {
            loadByteCode(allClasspath)
            persistent("jdbc:postgresql://localhost:5432/opentaint-ir?user=postgres&password=root",
                clearOnStart = true,
                PredefinedPersistenceType.POSTGRES
            )
            installFeatures(InMemoryHierarchy, Usages, Builders)
        }.also {
            println("AWAITING db took ${System.currentTimeMillis() - start}ms")
            start = System.currentTimeMillis()
            it.awaitBackgroundJobs()
            println("AWAITING jobs took ${System.currentTimeMillis() - start}ms")
        }
        db.persistence.read {
            println("Processed classes " + it.fetchCount(CLASSES))
            println("Processed fields " + it.fetchCount(FIELDS))
            println("Processed methods " + it.fetchCount(METHODS))
            println("Processed method params "+ it.fetchCount(METHODPARAMETERS))
        }

//        val name = ManagementFactory.getRuntimeMXBean().name
//        val pid = name.split("@")[0]
//        println("Taking memory dump from $pid....")
//        val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//        process.waitFor()
        println(db)
    }
}