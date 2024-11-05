package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRBoundedWildcard
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRUnboundWildcard

class JIRUnboundWildcardImpl(override val classpath: JIRClasspath, override val nullable: Boolean = true) :
    JIRUnboundWildcard {

    override val typeName: String
        get() = "*"

    override fun notNullable(): JIRRefType {
        return JIRUnboundWildcardImpl(classpath, false)
    }
}

class JIRBoundedWildcardImpl(
    override val upperBounds: List<JIRRefType>,
    override val lowerBounds: List<JIRRefType>,
    override val nullable: Boolean
) : JIRBoundedWildcard {

    override val classpath: JIRClasspath
        get() = upperBounds.firstOrNull()?.classpath ?: lowerBounds.firstOrNull()?.classpath
        ?: throw IllegalStateException("Upper or lower bound should be specified")

    override val typeName: String
        get() {
            val (name, bounds) = when{
                upperBounds.isNotEmpty() -> "extends" to upperBounds
                else -> "super" to lowerBounds
            }
            return "? $name ${bounds.joinToString(" & ") { it.typeName }}"
        }


    override fun notNullable(): JIRRefType {
        return JIRBoundedWildcardImpl(upperBounds, lowerBounds, false)
    }
}


class JIRTypeVariableImpl(
    override val classpath: JIRClasspath,
    private val declaration: JIRTypeVariableDeclaration,
    override val nullable: Boolean
) : JIRTypeVariable {

    override val typeName: String
        get() = symbol

    override val symbol: String get() = declaration.symbol

    override val bounds: List<JIRRefType>
        get() = declaration.bounds

    override fun notNullable(): JIRRefType {
        return JIRTypeVariableImpl(classpath, declaration, nullable)
    }
}