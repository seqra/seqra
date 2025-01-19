package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRBoundedWildcard
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRUnboundWildcard
import org.opentaint.ir.api.ext.objectClass
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRUnboundWildcardImpl(override val classpath: JIRClasspath) :
    JIRUnboundWildcard {

    override val nullable: Boolean = true

    override val typeName: String
        get() = "*"

    override fun copyWithNullability(nullability: Boolean?): JIRRefType {
        if (nullability != true)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        return this
    }
}

class JIRBoundedWildcardImpl(
    override val upperBounds: List<JIRRefType>,
    override val lowerBounds: List<JIRRefType>,
) : JIRBoundedWildcard {
    override val nullable: Boolean = true

    override val classpath: JIRClasspath
        get() = upperBounds.firstOrNull()?.classpath ?: lowerBounds.firstOrNull()?.classpath
        ?: throw IllegalStateException("Upper or lower bound should be specified")

    override val typeName: String
        get() {
            val (name, bounds) = when {
                upperBounds.isNotEmpty() -> "extends" to upperBounds
                else -> "super" to lowerBounds
            }
            return "? $name ${bounds.joinToString(" & ") { it.typeName }}"
        }

    override val jIRClass: JIRClassOrInterface by lazy(PUBLICATION) {
        val obj = classpath.objectClass
        lowerBounds.firstNotNullOfOrNull { it.jIRClass.takeIf { it != obj } } ?: obj
    }

    override fun copyWithNullability(nullability: Boolean?): JIRRefType {
        if (nullability != true)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        return this
    }
}

class JIRTypeVariableImpl(
    override val classpath: JIRClasspath,
    private val declaration: JIRTypeVariableDeclaration,
    override val nullable: Boolean?
) : JIRTypeVariable {

    override val typeName: String
        get() = symbol

    override val symbol: String get() = declaration.symbol

    override val bounds: List<JIRRefType>
        get() = declaration.bounds

    override val jIRClass: JIRClassOrInterface by lazy(PUBLICATION) {
        val obj = classpath.objectClass
        bounds.firstNotNullOfOrNull { it.jIRClass.takeIf { it != obj } } ?: obj
    }

    override fun copyWithNullability(nullability: Boolean?): JIRRefType {
        return JIRTypeVariableImpl(classpath, declaration, nullability)
    }
}