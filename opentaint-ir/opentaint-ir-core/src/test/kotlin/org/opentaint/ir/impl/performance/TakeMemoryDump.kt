package org.opentaint.ir

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.impl.allClasspath
import org.opentaint.ir.impl.storage.jooq.tables.references.CLASSES
import org.opentaint.ir.impl.storage.jooq.tables.references.FIELDS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.opentaint.ir.impl.storage.jooq.tables.references.METHODS

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = jirdb {
            loadByteCode(allClasspath)
            persistent("D:\\work\\jirdb\\jirdb.db")
//            installFeatures(Usages)
        }.also {
            println("AWAITING db took ${System.currentTimeMillis() - start}ms")
            start = System.currentTimeMillis()
            it.awaitBackgroundJobs()
            println("AWAITING jobs took ${System.currentTimeMillis() - start}ms")
        }
        db.persistence.read {
            println("Classes " + it.fetchCount(CLASSES))
            println("Methods " + it.fetchCount(METHODS))
            println("Methods params "+ it.fetchCount(METHODPARAMETERS))
            println("Fields " + it.fetchCount(FIELDS))
        }

//        val name = ManagementFactory.getRuntimeMXBean().name
//        val pid = name.split("@")[0]
//        println("Taking memory dump from $pid....")
//        val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//        process.waitFor()
        println(db)
    }
}