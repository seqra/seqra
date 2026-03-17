package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRDeclaration
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.RegisteredLocation

class JIRDeclarationImpl(override val location: RegisteredLocation, override val relativePath: String) : JIRDeclaration {

    companion object {
        fun of(location: RegisteredLocation, clazz: JIRClassOrInterface): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, clazz.name)
        }

        fun of(location: RegisteredLocation, method: JIRMethod): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, "${method.enclosingClass.name}#${method.name}")
        }

        fun of(location: RegisteredLocation, field: JIRField): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, "${field.enclosingClass.name}#${field.name}")
        }

        fun of(location: RegisteredLocation, param: JIRParameter): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, "${param.method.enclosingClass.name}#${param.name}:${param.index}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRDeclarationImpl

        if (location != other.location) return false
        if (relativePath != other.relativePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = location.hashCode()
        result = 31 * result + relativePath.hashCode()
        return result
    }

}
