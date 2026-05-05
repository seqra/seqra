package org.opentaint.ir.impl.python.flat

/**
 * Operand of a [FlatInst]. Either a constant, a local/temporary variable,
 * or a reference to a globally-scoped name.
 */
sealed interface FlatValue

data class FlatLocal(val name: String, val type: FlatType = FlatAnyType) : FlatValue
data class FlatGlobalRef(val name: String, val module: String) : FlatValue

/**
 * Reference to an entire module (e.g. `os` in `os.getcwd()`, or `p` after
 * `import os.path as p`). Distinct from [FlatGlobalRef], which names a
 * value defined inside a module.
 *
 * `module` holds the canonical fullname (`os`, `os.path`); aliases are
 * resolved at lowering time so downstream consumers only ever see the
 * canonical name. Attribute access on a module (`p.join` → `os.path.join`)
 * stays as a regular `FlatLoadAttr` whose object is this ref.
 */
data class FlatModuleRef(val module: String) : FlatValue

sealed interface FlatConst : FlatValue
data class FlatIntConst(val value: Long) : FlatConst
data class FlatFloatConst(val value: Double) : FlatConst
data class FlatStrConst(val value: String) : FlatConst
data class FlatBoolConst(val value: Boolean) : FlatConst
data object FlatNoneConst : FlatConst
data object FlatEllipsisConst : FlatConst
data class FlatBytesConst(val value: ByteArray) : FlatConst {
    override fun equals(other: Any?) = this === other || (other is FlatBytesConst && value.contentEquals(other.value))
    override fun hashCode() = value.contentHashCode()
}
data class FlatComplexConst(val real: Double, val imag: Double) : FlatConst
