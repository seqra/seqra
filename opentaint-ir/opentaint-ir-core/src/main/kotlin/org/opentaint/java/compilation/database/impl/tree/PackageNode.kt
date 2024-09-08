package org.opentaint.java.compilation.database.impl.tree

import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource

class PackageNode(folderName: String?, parent: PackageNode?) : AbstractNode(folderName, parent) {

    // folderName -> subpackage
    private val subpackages = Persistent23TreeMap<String, PackageNode>()

    // simpleName -> (version -> node)
    private val classes = Persistent23TreeMap<String, Persistent23TreeMap<String, ClassNode>>()

    fun findPackageOrNew(subfolderName: String): PackageNode {
        return subpackages.getOrPut(subfolderName) {
            PackageNode(subfolderName, this@PackageNode)
        }
    }

    fun findPackageOrNull(subfolderName: String): PackageNode? {
        return subpackages.read {
            get(subfolderName)
        }
    }

    fun findClassOrNew(simpleClassName: String, version: String, source: ClassByteCodeSource): ClassNode {
        val nameIndex = classes.getOrPut(simpleClassName) {
            Persistent23TreeMap()
        }
        return nameIndex.getOrPut(version) { ClassNode(simpleClassName, this, source) }
    }

    fun firstClassOrNull(className: String, version: String): ClassNode? {
        return classes.read {
            get(className)?.read {
                get(version)
            }
        }
    }

    fun filterClassNodes(className: String, predicate: (ClassNode) -> Boolean): List<ClassNode> {
        return classes.beginRead().get(className)?.read {
            asSequence().filter { predicate(it.value) }.map { it.value }.toList()
        }.orEmpty()
    }

    fun firstClassOrNull(className: String, predicate: (String) -> Boolean): ClassNode? {
        val versioned = classes.beginRead().get(className) ?: return null
        return versioned.read {
            asSequence().first { predicate(it.key) }
        }?.value
    }

    fun visit(visitor: NodeVisitor) {
        visitor.visitPackageNode(this)
        subpackages.beginRead().iterator().forEachRemaining {
            visitor.visitPackageNode(it.value)
        }
    }


    fun dropVersion(version: String) {
        subpackages.writeFinally {
            remove(version)
        }
        classes.writeFinally {
            forEach {
                it.value.writeFinally {
                    remove(version)
                    forEach {
                        it.value.removeSubTypesOfVersion(version)
                    }
                }
            }
        }
    }
}

private fun <T> Persistent23TreeMap<String, T>.getOrPut(key: String, default: () -> T): T {
    val node = read {
        get(key)
    }
    if (node != null) {
        return node
    }
    val newNode: T = default()
    val result = write {
        put(key, newNode)
    }
    if (result) {
        return newNode
    }
    return getOrPut(key, default)
}


inline fun <K : Comparable<K>, V, R> Persistent23TreeMap<K, V>.read(block: Persistent23TreeMap.ImmutableMap<K, V>.() -> R): R {
    return beginRead().block()
}

inline fun <K : Comparable<K>, V> Persistent23TreeMap<K, V>.write(block: Persistent23TreeMap.MutableMap<K, V>.() -> Unit): Boolean {
    val mutableMap = beginWrite()
    mutableMap.block()
    return mutableMap.endWrite()
}

inline fun <K : Comparable<K>, V> Persistent23TreeMap<K, V>.writeFinally(block: Persistent23TreeMap.MutableMap<K, V>.() -> Unit) {
    while (!write(block)) {
    }
}
