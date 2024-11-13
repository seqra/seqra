package org.opentaint.ir

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.impl.performance.allIdeaJarsAbsolute
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.FIELDS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODS

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = jirdb {
            loadByteCode(allIdeaJarsAbsolute)
            persistent("D:\\work\\jirdb\\jirdb-idea.db")
//            installFeatures(Usages)
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