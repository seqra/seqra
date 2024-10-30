package org.opentaint.ir.impl.types

import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRParametrizedType
import org.opentaint.ir.api.JIRRefType
import org.opentaint.ir.api.JIRTypeVariableDeclaration
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.JIRTypedMethod

class JIRParameterizedTypeImpl(
    override val jirClass: JIRClassOrInterface,
    override val originParametrization: List<JIRTypeVariableDeclaration>,
    override val parametrization: List<JIRRefType>,
    override val nullable: Boolean
) : JIRParametrizedType {

    override val classpath: JIRClasspath
        get() = jirClass.classpath

    override val typeName: String
        get() = "${jirClass.name}<${parametrization.joinToString { it.typeName }}>"

    override val methods: List<JIRTypedMethod>
        get() = TODO("Not yet implemented")

    override val fields: List<JIRTypedField>
        get() = TODO("Not yet implemented")

    override fun notNullable() = JIRParameterizedTypeImpl(jirClass, originParametrization, parametrization, false)

}