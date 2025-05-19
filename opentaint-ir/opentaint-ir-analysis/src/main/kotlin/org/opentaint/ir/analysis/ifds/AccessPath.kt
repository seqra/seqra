package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRSimpleValue
import org.opentaint.ir.api.jvm.cfg.JIRValue

data class AccessPath internal constructor(
    val value: CommonValue?,
    val accesses: List<Accessor>,
) {
    fun limit(n: Int): AccessPath = AccessPath(value, accesses.take(n))

    operator fun plus(accesses: List<Accessor>): AccessPath {
        // for (accessor in accesses) {
        //     if (accessor is FieldAccessor && (accessor.field as JIRField).isStatic) {
        //         throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        //     }
        // }

        return AccessPath(value, this.accesses + accesses)
    }

    operator fun plus(accessor: Accessor): AccessPath {
        // if (accessor is FieldAccessor && (accessor.field as JIRField).isStatic) {
        //     throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        // }

        return AccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }
}

val AccessPath.isOnHeap: Boolean
    get() = accesses.isNotEmpty()

val AccessPath.isStatic: Boolean
    get() = value == null

operator fun AccessPath.minus(other: AccessPath): List<Accessor>? {
    if (value != other.value) return null
    if (accesses.take(other.accesses.size) != other.accesses) return null
    return accesses.drop(other.accesses.size)
}

// interface CommonAccessPath {
//     val value: CommonValue?
//     val accesses: List<Accessor>
//
//     fun limit(n: Int): CommonAccessPath
//
//     operator fun plus(accesses: List<Accessor>): CommonAccessPath
//     operator fun plus(accessor: Accessor): CommonAccessPath
// }
//
// val CommonAccessPath.isOnHeap: Boolean
//     get() = accesses.isNotEmpty()
//
// val CommonAccessPath.isStatic: Boolean
//     get() = value == null
//
// operator fun CommonAccessPath.minus(other: CommonAccessPath): List<Accessor>? {
//     if (value != other.value) return null
//     if (accesses.take(other.accesses.size) != other.accesses) return null
//     return accesses.drop(other.accesses.size)
// }
//
// fun CommonExpr.toPathOrNull(): CommonAccessPath? = when (this) {
//     // is JIRExpr -> toPathOrNull()
//     is CommonValue -> toPathOrNull()
//     else -> error("Cannot")
// }
//
// fun CommonValue.toPathOrNull(): CommonAccessPath? = when (this) {
//     // is JIRValue -> toPathOrNull()
//     else -> error("Cannot")
// }
//
// fun CommonValue.toPath(): CommonAccessPath = when (this) {
//     // is JIRValue -> toPath()
//     else -> error("Cannot")
// }
//
// /**
//  * This class is used to represent an access path that is needed for problems
//  * where dataflow facts could be correlated with variables/values
//  * (such as NPE, uninitialized variable, etc.)
//  */
// data class JIRAccessPath internal constructor(
//     override val value: JIRSimpleValue?, // null for static field
//     override val accesses: List<Accessor>,
// ) : CommonAccessPath {
//     init {
//         if (value == null) {
//             require(accesses.isNotEmpty())
//             val a = accesses[0]
//             require(a is FieldAccessor)
//             require(a.field is JIRField)
//             require(a.field.isStatic)
//         }
//     }
//
//     override fun limit(n: Int): JIRAccessPath = JIRAccessPath(value, accesses.take(n))
//
//     override operator fun plus(accesses: List<Accessor>): JIRAccessPath {
//         for (accessor in accesses) {
//             if (accessor is FieldAccessor && (accessor.field as JIRField).isStatic) {
//                 throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
//             }
//         }
//
//         return JIRAccessPath(value, this.accesses + accesses)
//     }
//
//     override operator fun plus(accessor: Accessor): JIRAccessPath {
//         if (accessor is FieldAccessor && (accessor.field as JIRField).isStatic) {
//             throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
//         }
//
//         return JIRAccessPath(value, this.accesses + accessor)
//     }
//
//     override fun toString(): String {
//         return value.toString() + accesses.joinToString("") { it.toSuffix() }
//     }
//
//     companion object {
//         fun from(value: JIRSimpleValue): JIRAccessPath = JIRAccessPath(value, emptyList())
//
//         fun from(field: JIRField): JIRAccessPath {
//             require(field.isStatic) { "Expected static field" }
//             return JIRAccessPath(null, listOf(FieldAccessor(field)))
//         }
//     }
// }

fun JIRExpr.toPathOrNull(): AccessPath? = when (this) {
    is JIRValue -> toPathOrNull()
    is JIRCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JIRValue.toPathOrNull(): AccessPath? = when (this) {
    is JIRSimpleValue -> AccessPath(this, emptyList())

    is JIRArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JIRFieldRef -> {
        val instance = instance
        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            AccessPath(null, listOf(FieldAccessor(field.field)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.field)
            }
        }
    }

    else -> null
}

fun JIRValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

//     override val accesses: List<Accessor>,
// ) : CommonAccessPath {
//
//     init {
//         if (value == null) {
//             require(accesses.isNotEmpty())
//             val a = accesses[0]
//             require(a is FieldAccessor)
//             require(a.field is JIRField)
//             require(a.field.isStatic)
//         }
//     }
//
//
//         // for (accessor in accesses) {
//         //         throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
//         //     }
//         // }
//
//     }
//
//         //     throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
//         // }
//
//     }
//
//     override fun toString(): String {
//         return value.toString() + accesses.joinToString("") { it.toSuffix() }
//     }
//
//     companion object {
//
//             // require(field.isStatic) { "Expected static field" }
//         }
//     }
// }

    else -> null
}

    }

    }

    else -> null
}

    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
