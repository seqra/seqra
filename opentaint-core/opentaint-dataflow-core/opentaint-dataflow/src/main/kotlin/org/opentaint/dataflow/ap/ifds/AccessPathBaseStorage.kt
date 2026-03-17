package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonInst

abstract class AccessPathBaseStorage<V : Any>(initialStatement: CommonInst) {
    @JvmField
    var thisStorage: V? = null

    @JvmField
    var returnStorage: V? = null

    @JvmField
    var exceptionStorage: V? = null

    @JvmField
    val argsStorage = arrayOfNulls<Any?>(initialStatement.location.method.parameters.size)

    abstract fun createStorage(): V

    abstract fun getOrCreateLocal(idx: Int): V
    abstract fun findLocal(idx: Int): V?
    abstract fun forEachLocalValue(body: (AccessPathBase, V) -> Unit)

    abstract fun getOrCreateClassStatic(base: AccessPathBase.ClassStatic): V
    abstract fun findClassStatic(base: AccessPathBase.ClassStatic): V?
    abstract fun forEachClassStaticValue(body: (AccessPathBase, V) -> Unit)

    abstract fun getOrCreateConstant(base: AccessPathBase.Constant): V
    abstract fun findConstant(base: AccessPathBase.Constant): V?
    abstract fun forEachConstantValue(body: (AccessPathBase, V) -> Unit)

    fun getOrCreate(base: AccessPathBase): V = when (base) {
        AccessPathBase.This -> {
            thisStorage ?: createStorage().also { thisStorage = it }
        }

        is AccessPathBase.Argument -> {
            val idx = base.idx
            check(idx in argsStorage.indices) { "Incorrect storage fact base: $base" }

            val storage = argsStorage[idx] ?: createStorage().also { argsStorage[idx] = it }

            @Suppress("UNCHECKED_CAST")
            storage as V
        }

        is AccessPathBase.ClassStatic -> getOrCreateClassStatic(base)
        is AccessPathBase.LocalVar -> getOrCreateLocal(base.idx)
        is AccessPathBase.Constant -> getOrCreateConstant(base)

        AccessPathBase.Return -> {
            returnStorage ?: createStorage().also { returnStorage = it }
        }

        AccessPathBase.Exception -> {
            exceptionStorage ?: createStorage().also { exceptionStorage = it }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun find(base: AccessPathBase): V? = when (base) {
        AccessPathBase.This -> thisStorage
        is AccessPathBase.Argument -> argsStorage.getOrNull(base.idx) as V?
        is AccessPathBase.ClassStatic -> findClassStatic(base)
        is AccessPathBase.LocalVar -> findLocal(base.idx)
        is AccessPathBase.Constant -> findConstant(base)
        AccessPathBase.Return -> returnStorage
        AccessPathBase.Exception -> exceptionStorage
    }

    override fun toString(): String = buildString {
        forEachValue { base, value ->
            appendLine("($base: $value)")
        }
    }

    inline fun forEachValue(crossinline body: (AccessPathBase, V) -> Unit) {
        thisStorage?.let { body(AccessPathBase.This, it) }

        returnStorage?.let { body(AccessPathBase.Return, it) }

        exceptionStorage?.let { body(AccessPathBase.Exception, it) }

        for ((index, storage) in argsStorage.withIndex()) {
            if (storage == null) continue

            @Suppress("UNCHECKED_CAST")
            body(AccessPathBase.Argument(index), storage as V)
        }

        forEachLocalValue { base, value -> body(base, value) }
        forEachClassStaticValue { base, value -> body(base, value) }
        forEachConstantValue { base, value -> body(base, value) }
    }
}
