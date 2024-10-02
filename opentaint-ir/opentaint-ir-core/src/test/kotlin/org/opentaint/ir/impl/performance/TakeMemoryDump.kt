package org.opentaint.ir.impl.performance

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.impl.index.ReversedUsages
import org.opentaint.ir.jirdb
import java.lang.management.ManagementFactory


val db = runBlocking {
    jirdb {
        installFeatures(ReversedUsages)
        useProcessJavaRuntime()
    }.also {
        it.awaitBackgroundJobs()
    }
}


fun main() {
    println(db)
    val name = ManagementFactory.getRuntimeMXBean().name
    val pid = name.split("@")[0]
    println("Taking memory dump from $pid....")
    val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
    process.waitFor()
}