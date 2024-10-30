package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRBoundWildcard
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRLowerBoundWildcard
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod
import org.opentaint.ir.api.JIRUnboundWildcard
import org.opentaint.ir.api.JIRUpperBoundWildcard

class JIRUnboundWildcardImpl(private val anyType: JIRClassType, override val nullable: Boolean = true) :
    JIRUnboundWildcard {

    override val classpath: JIRClasspath
        get() = anyType.classpath
    override val typeName: String
        get() = "*"

    override val jirClass: JIRClassOrInterface
        get() = anyType.jirClass
    override val methods: List<JIRTypedMethod>
        get() = anyType.methods
    override val fields: List<JIRTypedField>
        get() = anyType.fields

    override fun notNullable(): JIRRefType {
        return JIRUnboundWildcardImpl(anyType, false)
    }
}

abstract class AbstractJcBoundWildcard(override val boundType: JIRRefType, override val nullable: Boolean) :
    JIRBoundWildcard {

    override val jirClass: JIRClassOrInterface
        get() = boundType.jirClass
    override val methods: List<JIRTypedMethod>
        get() = boundType.methods

    override val fields: List<JIRTypedField>
        get() = boundType.fields

    override val classpath: JIRClasspath
        get() = boundType.classpath
}

class JIRUpperBoundWildcardImpl(boundType: JIRRefType, nullable: Boolean) : AbstractJcBoundWildcard(boundType, nullable),
    JIRUpperBoundWildcard {

    override val typeName: String
        get() = "? extends ${boundType.typeName}"

    override fun notNullable(): JIRRefType {
        return JIRUpperBoundWildcardImpl(boundType, false)
    }
}

class JIRLowerBoundWildcardImpl(boundType: JIRRefType, nullable: Boolean) : AbstractJcBoundWildcard(boundType, nullable),
    JIRLowerBoundWildcard {

    override val typeName: String
        get() = "? super ${boundType.typeName}"

    override fun notNullable(): JIRRefType {
        return JIRLowerBoundWildcardImpl(boundType, false)
    }
}

class JIRTypeVariableImpl(
    override val typeSymbol: String,
    override val nullable: Boolean,
    private val anyType: JIRClassType
) : JIRTypeVariable {

    override val classpath: JIRClasspath
        get() = anyType.classpath

    override val typeName: String
        get() = typeSymbol

    override val methods: List<JIRTypedMethod>
        get() = anyType.methods

    override val fields: List<JIRTypedField>
        get() = anyType.fields

    override val jirClass: JIRClassOrInterface
        get() = anyType.jirClass

    override fun notNullable(): JIRRefType {
        return JIRTypeVariableImpl(typeSymbol, false, anyType)
    }
}