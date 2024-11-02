package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRBoundedWildcard
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRLowerBoundWildcard

import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRUnboundWildcard
import org.opentaint.ir.api.JIRUpperBoundWildcard

class JIRUnboundWildcardImpl(override val classpath: JIRClasspath, override val nullable: Boolean = true) :
    JIRUnboundWildcard {

    override val typeName: String
        get() = "*"

    override fun notNullable(): JIRRefType {
        return JIRUnboundWildcardImpl(classpath, false)
    }
}

abstract class JIRAbstractBoundedWildcard(override val boundType: JIRRefType, override val nullable: Boolean) :
    JIRBoundedWildcard {
    override val classpath: JIRClasspath
        get() = boundType.classpath

}


class JIRLowerBoundWildcardImpl(boundType: JIRRefType, nullable: Boolean) :
    JIRAbstractBoundedWildcard(boundType, nullable), JIRLowerBoundWildcard {

    override val typeName: String
        get() = "? extends ${boundType.typeName}"

    override fun notNullable(): JIRRefType {
        return JIRLowerBoundWildcardImpl(boundType, false)
    }
}

class JIRUpperBoundWildcardImpl(boundType: JIRRefType, nullable: Boolean) :
    JIRAbstractBoundedWildcard(boundType, nullable), JIRUpperBoundWildcard {

    override val typeName: String
        get() = "? super ${boundType.typeName}"

    override fun notNullable(): JIRRefType {
        return JIRUpperBoundWildcardImpl(boundType, false)
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