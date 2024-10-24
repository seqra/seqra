package org.opentaint.ir.impl.bytecode

import org.opentaint.ir.api.JIRByteCodeLocation
import org.opentaint.ir.api.JIRClassOrInterface
import org.opentaint.ir.api.JIRDeclaration
import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter

class JIRDeclarationImpl(override val location: JIRByteCodeLocation, override val relativePath: String) : JIRDeclaration {

    companion object {
        fun of(location: JIRByteCodeLocation, clazz: JIRClassOrInterface): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, clazz.name)
        }

        fun of(location: JIRByteCodeLocation, method: JIRMethod): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, "${method.jirClass.name}#${method.name}")
        }

        fun of(location: JIRByteCodeLocation, field: JIRField): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, "${field.jirClass.name}#${field.name}")
        }

        fun of(location: JIRByteCodeLocation, param: JIRParameter): JIRDeclarationImpl {
            return JIRDeclarationImpl(location, "${param.method.jirClass.name}#${param.name}:${param.index}")
        }
    }
}