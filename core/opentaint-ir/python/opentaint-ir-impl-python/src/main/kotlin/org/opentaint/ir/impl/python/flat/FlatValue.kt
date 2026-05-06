package org.opentaint.ir.impl.python.flat

/**
 * Operand of a [FlatInst]. Either a constant, a local/temporary variable,
 * or a reference to a globally-scoped name.
 */
sealed interface FlatValue

data class FlatLocal(val name: String, val type: FlatType = FlatAnyType) : FlatValue

/**
 * Reference to a globally-resolvable name, identified by its qualified name.
 * For intra-module functions [qualifiedName] equals the corresponding
 * [FlatFunctionIR.qualifiedName]; for builtins / cross-module imports it is
 * the canonical dotted fullname (`builtins.AssertionError`, `os.getcwd`).
 *
 * The single string is the resolution key. Module-ness is a property of the
 * resolved target, not of the reference, so consumers that care about a
 * "module" component (e.g. PIR class-method dispatch) split the string at
 * the relevant boundary themselves.
 */
data class FlatGlobalRef(val qualifiedName: String) : FlatValue

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
