@file:JvmName("JIRGraphs")

package org.opentaint.ir.api.jvm.cfg

abstract class TypedExprResolver<T : JIRExpr> : AbstractFullExprSetCollector() {
    val result = hashSetOf<T>()
}

class LocalResolver : TypedExprResolver<JIRLocal>() {
    override fun ifMatches(expr: JIRExpr) {
        if (expr is JIRLocal) {
            result.add(expr)
        }
    }
}

class ValueResolver : TypedExprResolver<JIRValue>() {
    override fun ifMatches(expr: JIRExpr) {
        if (expr is JIRValue) {
            result.add(expr)
        }
    }
}

fun JIRGraph.apply(visitor: JIRInstVisitor<Unit>): JIRGraph {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JIRInstVisitor<E>> JIRGraph.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> JIRGraph.collect(visitor: JIRInstVisitor<T>): Collection<T> {
    return instructions.map { it.accept(visitor) }
}

fun <R, E, T : JIRInstVisitor<E>> JIRInst.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

fun <R, E, T : JIRExprVisitor<E>> JIRExpr.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

val JIRGraph.locals: Set<JIRLocal>
    get() {
        val resolver = LocalResolver().also {
            collect(it)
        }
        return resolver.result
    }

val JIRInst.locals: Set<JIRLocal>
    get() {
        val resolver = LocalResolver().also {
            accept(it)
        }
        return resolver.result
    }

val JIRExpr.values: Set<JIRValue>
    get() {
        val resolver = ValueResolver().also {
            accept(it)
        }
        return resolver.result
    }

val JIRInst.values: Set<JIRValue>
    get() {
        val resolver = ValueResolver().also {
            accept(it)
        }
        return resolver.result
    }

val JIRGraph.values: Set<JIRValue>
    get() {
        val resolver = ValueResolver().also {
            collect(it)
        }
        return resolver.result
    }
