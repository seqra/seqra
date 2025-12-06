@file:JvmName("JIRFields")
package org.opentaint.ir.api.jvm.ext

import org.opentaint.ir.api.jvm.JIRField
import org.objectweb.asm.Opcodes

/**
 * is item has `volatile` modifier
 */
val JIRField.isVolatile: Boolean
    get() {
        return access and Opcodes.ACC_VOLATILE != 0
    }

/**
 * is field has `transient` modifier
 */
val JIRField.isTransient: Boolean
    get() {
        return access and Opcodes.ACC_TRANSIENT != 0
    }

val JIRField.isEnum: Boolean
    get() {
        return access and Opcodes.ACC_ENUM != 0
    }
