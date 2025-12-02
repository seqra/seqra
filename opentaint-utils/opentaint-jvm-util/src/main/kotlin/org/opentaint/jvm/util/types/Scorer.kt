package org.opentaint.jvm.util.types

import org.opentaint.ir.api.jvm.ext.CONSTRUCTOR
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.lang.reflect.Modifier

fun scoreClassNode(location: RegisteredLocation, node: ClassNode): Double {
    var score = 0.0

    if (location.isRuntime) {
        if (!node.name.startsWith("java/")) {
            score -= 1000
        }
    } else {
        score += 2
    }

    // prefer class types over arrays
    if (Modifier.isPublic(node.access)) {
        score += 4
    }

    if (!Modifier.isAbstract(node.access) && !Modifier.isInterface(node.access)) {
        score += 3
    }

    score -= node.fields.size / 10.0

    // Prefer easy instantiable classes
    val emptyPublicConstructorPresents = node.methods.any {
        it.name == CONSTRUCTOR && Modifier.isPublic(it.access) && Type.getArgumentTypes(it.desc).isEmpty()
    }
    if (emptyPublicConstructorPresents) {
        score += 5
    } else {
        val publicConstructorPresents = node.methods.any { it.name == CONSTRUCTOR && Modifier.isPublic(it.access) }
        if (publicConstructorPresents) {
            score += 3
        }
    }

    if (Modifier.isFinal(node.access)) {
        score += 2
    }

    if (node.outerClass == null) {
        score += 3
    }

    return score
}


