package org.opentaint.dataflow.configuration.jvm

import org.opentaint.ir.api.jvm.ByteCodeIndexer
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRDatabase
import org.opentaint.ir.api.jvm.JIRFeature
import org.opentaint.ir.api.jvm.JIRSignal
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.storage.StorageContext
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap

class JIRClassNameFeature: JIRFeature<Any?, Any?> {
    private val indexers = ConcurrentHashMap<Long, NameIndexer>()

    override fun newIndexer(opentaint-ir: JIRDatabase, location: RegisteredLocation): ByteCodeIndexer =
        indexers.getOrPut(location.id) { NameIndexer(location) }

    override suspend fun query(classpath: JIRClasspath, req: Any?): Sequence<Any?> = emptySequence()

    override fun onSignal(signal: JIRSignal) {
        // todo: persistence
    }

    fun filterClassesTo(cp: JIRClasspath, classes: MutableList<String>, className: String? = null) {
        cp.registeredLocations.forEach { location ->
            indexers[location.id]?.filterClassesTo(classes, className)
        }
    }

    private class NameIndexer(private val location: RegisteredLocation) : ByteCodeIndexer {
        private val classesByName = ConcurrentHashMap<String, MutableList<String>>()

        override fun index(classNode: ClassNode) {
            val name = classNode.name
            val nameParts = name.split("/")
            val simpleName = nameParts.last()
            val opentaint-irName = nameParts.joinToString(".")
            classesByName.computeIfAbsent(simpleName) { mutableListOf() }.add(opentaint-irName)
        }

        fun filterClassesTo(classes: MutableList<String>, className: String? = null) {
            if (className != null) {
                classesByName[className]?.let { classes.addAll(it) }
                return
            }

            allClasses(classes)
        }

        private fun allClasses(classes: MutableList<String>) {
            classesByName.values.forEach { classes.addAll(it) }
        }

        override fun flush(context: StorageContext) {
            // todo: persistence
        }
    }
}
