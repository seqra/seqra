package org.opentaint.ir.impl.python.builder

/**
 * Variable scope tracking for PIR lowering.
 * Manages temp variable allocation and local name resolution.
 */
class Scope {
    private val locals = mutableMapOf<String, String>()
    private var tempCounter = 0

    fun newTemp(): String {
        val name = "\$t${tempCounter}"
        tempCounter++
        return name
    }

    fun resolveLocal(name: String): String {
        return locals.getOrPut(name) { name }
    }
}

/**
 * Stack of scopes for nested functions/closures.
 */
class ScopeStack {
    private val scopes = mutableListOf(Scope())

    val current: Scope get() = scopes.last()

    fun newTemp(): String = current.newTemp()

    fun resolveLocal(name: String): String = current.resolveLocal(name)
}
