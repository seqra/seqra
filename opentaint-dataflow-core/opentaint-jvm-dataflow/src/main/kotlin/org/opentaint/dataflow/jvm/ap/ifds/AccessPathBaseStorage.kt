package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst

abstract class AccessPathBaseStorage<V : Any>(initialStatement: JIRInst) {
    private var thisStorage: V? = null
    private val argsStorage = arrayOfNulls<Any?>(initialStatement.location.method.parameters.size)

    abstract fun createStorage(): V

    abstract fun getOrCreateLocal(idx: Int): V
    abstract fun findLocal(idx: Int): V?
    abstract fun <R : Any> mapLocalValues(body: (AccessPathBase, V) -> R): Sequence<R>

    abstract fun getOrCreateClassStatic(base: AccessPathBase.ClassStatic): V
    abstract fun findClassStatic(base: AccessPathBase.ClassStatic): V?
    abstract fun <R : Any> mapClassStaticValues(body: (AccessPathBase, V) -> R): Sequence<R>

    abstract fun getOrCreateConstant(base: AccessPathBase.Constant): V
    abstract fun findConstant(base: AccessPathBase.Constant): V?
    abstract fun <R : Any> mapConstantValues(body: (AccessPathBase, V) -> R): Sequence<R>

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
    }

    @Suppress("UNCHECKED_CAST")
    fun find(base: AccessPathBase): V? = when (base) {
        AccessPathBase.This -> thisStorage
        is AccessPathBase.Argument -> argsStorage.getOrNull(base.idx) as V?
        is AccessPathBase.ClassStatic -> findClassStatic(base)
        is AccessPathBase.LocalVar -> findLocal(base.idx)
        is AccessPathBase.Constant -> findConstant(base)
    }

    fun <R: Any> mapValues(body: (AccessPathBase, V) -> R): Sequence<R> {
        val result = mutableListOf<R>()
        thisStorage?.let { result.add(body(AccessPathBase.This, it)) }

        @Suppress("UNCHECKED_CAST")
        (argsStorage as Array<V?>).mapIndexedNotNullTo(result) { argIdx, storage ->
            storage?.let { body(AccessPathBase.Argument(argIdx), it) }
        }

        return result.asSequence() + mapLocalValues(body) + mapConstantValues(body) + mapClassStaticValues(body)
    }

    override fun toString(): String = mapValues { base, v ->
        "($base: $v)"
    }.joinToString("\n")
}
