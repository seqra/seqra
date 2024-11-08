package org.opentaint.ir.impl.performance

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.opentaint.ir.impl.index.Usages
import org.opentaint.ir.impl.storage.ClassEntity
import org.opentaint.ir.impl.storage.FieldEntity
import org.opentaint.ir.impl.storage.MethodEntity
import org.opentaint.ir.impl.storage.MethodParameterEntity
import org.opentaint.ir.jirdb

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = jirdb {
//            predefinedDirOrJars = allClasspath
            persistent("D:\\work\\jirdb\\jirdb.db")
            installFeatures(Usages)
        }.also {
            println("AWAITING db took ${System.currentTimeMillis() - start}ms")
            start = System.currentTimeMillis()
            it.awaitBackgroundJobs()
            println("AWAITING jobs took ${System.currentTimeMillis() - start}ms")
        }
        transaction {
            println("Classes " + ClassEntity.count())
            println("Methods " + MethodEntity.count())
            println("Methods params " + MethodParameterEntity.count())
            println("Fields " + FieldEntity.count())
        }

//        val name = ManagementFactory.getRuntimeMXBean().name
//        val pid = name.split("@")[0]
//        println("Taking memory dump from $pid....")
//        val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//        process.waitFor()
        println(db)
    }
}
//
//fun main() {
//    println(db)
//    val name = ManagementFactory.getRuntimeMXBean().name
//    val pid = name.split("@")[0]
//    println("Taking memory dump from $pid....")
//    val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//    process.waitFor()
//}