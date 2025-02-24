package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRAccessible
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.ext.hasAnnotation
import org.opentaint.ir.api.ext.packageName

abstract class JIRAbstractLookup<Entry : JIRAccessible, Result : JIRAccessible>(protected var entry: Entry) {

    private var allowSearchPrivate: Boolean = true
    private val enterPointPackageName: String = entry.resolvePackage
    private var currentPackageName = entry.resolvePackage

    abstract val predicate: (Result) -> Boolean
    abstract val Entry.resolvePackage: String
    abstract fun Entry.next(): List<Entry>
    abstract val Entry.elements: List<Result>

    protected open fun lookupElement(en: Entry): Result? = en.elements.firstOrNull { matches(it) }

    private fun transit(entry: Entry, searchPrivate: Boolean) {
        this.entry = entry
        this.currentPackageName = entry.resolvePackage
        this.allowSearchPrivate = searchPrivate
    }

    fun lookup(): Result? {
        var workingList = listOf(entry)
        var searchPrivate = true
        while (workingList.isNotEmpty()) {
            workingList.forEach {
                transit(it, searchPrivate)
                lookupElement(it)?.let {
                    return it
                }
            }
            searchPrivate = false
            workingList = workingList.flatMap { it.next() }
        }
        return null
    }

    private fun matches(result: Result): Boolean {
        if (allowSearchPrivate) {
            return predicate(result)
        }
        return (result.isPublic || result.isProtected ||
                (result.isPackagePrivate && currentPackageName == enterPointPackageName)) && predicate(result)

    }

}

internal interface PolymorphicSignatureSupport {
    fun List<JIRMethod>.indexOf(name: String): Int {
        if (isEmpty()) {
            return -1
        }
        val packageName = first().enclosingClass.packageName
        if (packageName == "java.lang.invoke") {
            return indexOfFirst {
                it.name == name && it.hasAnnotation("java.lang.invoke.MethodHandle\$PolymorphicSignature")
            } // weak consumption. may fail
        }
        return -1
    }

    fun List<JIRMethod>.find(name: String, description: String): JIRMethod? {
        val index = indexOf(name)
        return if (index >= 0) get(index) else null
    }

    fun List<JIRTypedMethod>.find(name: String): JIRTypedMethod? {
        val index = map { it.method }.indexOf(name)
        return if (index >= 0) get(index) else null
    }
}