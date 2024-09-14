package org.opentaint.java.compilation.database.impl.tree

import jetbrains.exodus.core.dataStructures.persistent.Persistent23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.read
import jetbrains.exodus.core.dataStructures.persistent.write
import jetbrains.exodus.core.dataStructures.persistent.writeFinally

class PackageNode(folderName: String?, parent: PackageNode?) : AbstractNode<PackageNode>(folderName, parent) {

    // folderName -> subpackage
    internal val subpackages = Persistent23TreeMap<String, PackageNode>()

    // simpleName -> (locationId -> node)
    internal val classes = Persistent23TreeMap<String, Persistent23TreeMap<String, ClassNode>>()

    fun findPackageOrNull(subfolderName: String): PackageNode? {
        return subpackages.read {
            get(subfolderName)
        }
    }

    fun firstClassOrNull(className: String, locationId: String): ClassNode? {
        return classes.read {
            get(className)?.read {
                get(locationId)
            }
        }
    }

    fun filterClassNodes(className: String, predicate: (ClassNode) -> Boolean): List<ClassNode> {
        return classes.beginRead().get(className)?.read {
            asSequence().filter { predicate(it.value) }.map { it.value }.toList()
        }.orEmpty()
    }

    fun firstClassOrNull(className: String, predicate: (String) -> Boolean): ClassNode? {
        val locationsClasses = classes.beginRead().get(className) ?: return null
        return locationsClasses.read {
            asSequence().firstOrNull { predicate(it.key) }
        }?.value
    }

    fun visit(visitor: NodeVisitor) {
        visitor.visitPackageNode(this)
        subpackages.beginRead().iterator().forEachRemaining {
            visitor.visitPackageNode(it.value)
        }
    }

    fun dropLocation(locationId: String) {
        classes.writeFinally {
            forEach {
                it.value.writeFinally {
                    remove(locationId)
                    forEach {
                        it.value.removeSubTypesOf(locationId)
                    }
                }
            }
        }
    }
}

internal fun <T> Persistent23TreeMap<String, T>.getOrPut(key: String, default: () -> T): T {
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