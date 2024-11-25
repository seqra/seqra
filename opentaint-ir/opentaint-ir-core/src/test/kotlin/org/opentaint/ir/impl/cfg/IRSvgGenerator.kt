
package org.opentaint.ir.impl.cfg

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.methods
import org.opentaint.ir.impl.JIRGraphChecker
import org.opentaint.ir.impl.allClasspath
import org.opentaint.ir.jirdb
import java.io.Closeable
import java.io.File

class IRSvgGenerator(private val folder: File) : Closeable {

    private val db: JIRDB
    private val cp: JIRClasspath

    init {
        if (!folder.exists()) {
            folder.mkdir()
        } else {
            folder.list()?.forEach { File(folder, it).delete() }
        }
        db = runBlocking {
            jirdb {
                loadByteCode(allClasspath)
            }
        }
        cp = runBlocking { db.classpath(allClasspath) }
    }

    fun generate() {
        dumpClass(cp.findClass<IRExamples>())
    }

    private fun dumpClass(klass: JIRClassOrInterface) {
        klass.methods.filter { it.enclosingClass == klass }.mapIndexed { index, it ->
            val instructionList = it.instructionList()
            val fixedName = it.name.replace(Regex("[^A-Za-z0-9]"), "")
            val fileName = "${it.enclosingClass.simpleName}-$fixedName-$index.svg"
            val graph = instructionList.graph(it)
            JIRGraphChecker(graph).check()
            graph.toFile("dot", false, file = File(folder, "graph-$fileName"))
            graph.blockGraph().toFile("dot", file = File(folder, "block-graph-$fileName"))
        }
    }


    override fun close() {
        cp.close()
        db.close()
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        throw IllegalStateException("Please provide folder for target svgs")
    }
    val folder = args[0]
    IRSvgGenerator(folder = File(folder)).generate()
}