package org.opentaint.ir.impl.python.protoToFlat

import org.opentaint.ir.impl.python.flat.FlatGlobalRef
import org.opentaint.ir.impl.python.flat.FlatModuleRef
import org.opentaint.ir.impl.python.flat.FlatValue

/**
 * Per-scope record of `import` / `from … import …` bindings, used to recover
 * the canonical module / value path when mypy fails to resolve an import.
 *
 * Scopes form a chain: a nested function's manager has its enclosing
 * function's manager as parent; a top-level function's manager has the
 * module's manager as parent; the module's manager has no parent. [resolve]
 * walks inner → outer, returning the first hit — matching Python's name
 * resolution order. Writes go to the own scope only, so a child can shadow
 * an ancestor binding by recording the same name without disturbing the
 * ancestor's map.
 *
 * Mutation safety: parent writes that happen AFTER a child manager is
 * created but BEFORE the child manager is queried would be visible to the
 * child via the parent pointer. That doesn't happen in our pipeline today —
 * every child manager is queried synchronously while the enclosing scope's
 * CFG is being built, and parent `recordImport` / `recordImportFrom` calls
 * happen in source order through that same construction — so a child only
 * ever sees parent state up to the textual position of the child's def.
 * Keep this invariant in mind if the lowering pipeline ever becomes
 * non-linear.
 *
 * This class is intentionally proto-agnostic: callers unpack
 * `MypyImportStmtProto` / `MypyImportFromStmtProto` into the small string
 * tuples that [recordImport] and [recordImportFrom] accept. The derivation
 * rules (e.g. `import m.sub` binds the root `m`, `import m.sub as a` binds
 * `a` to canonical `m.sub`) live INSIDE those methods so all canonical-name
 * logic has one home.
 */
internal class ImportManager(private val parent: ImportManager? = null) {

    private val modules = mutableMapOf<String, String>()
    private val values = mutableMapOf<String, String>()

    /**
     * Record one entry of an `import` statement, given its written-source
     * `module` (e.g. `m.sub`) and optional `alias` (`""` when absent).
     * Derives the bound name and canonical module per Python's runtime rules:
     *
     * - `import m`          → bound=`m`,  canonical=`m`
     * - `import m.sub`      → bound=`m`,  canonical=`m`    — only the root is
     *                                                       bound; the deeper
     *                                                       module is reached
     *                                                       via attribute
     *                                                       access on it
     * - `import m as a`     → bound=`a`,  canonical=`m`
     * - `import m.sub as a` → bound=`a`,  canonical=`m.sub` — alias names the
     *                                                       deep module
     *
     * If the bound name was previously a value binding in this same scope
     * (e.g. `from pkg import x` then `import x`), the prior value binding
     * is dropped so that [resolve] sees only the latest write — matching
     * Python's rule that the textually-last binding wins within a scope.
     */
    fun recordImport(module: String, alias: String) {
        val bound: String
        val canonical: String
        if (alias.isNotEmpty()) {
            bound = alias
            canonical = module
        } else {
            // No alias: only the root segment becomes a name; the deeper
            // module is reached at runtime via attribute access on that root.
            bound = module.substringBefore('.')
            canonical = bound
        }
        values.remove(bound)
        modules[bound] = canonical
    }

    /**
     * Record one entry of a `from m import x [as y]` statement.
     *
     * [module] is the ALREADY-RESOLVED absolute module path — relative
     * imports must be resolved upstream (currently in `ast_serializer.py` via
     * `mypy.util.correct_relative_import`). When [module] is empty (mypy
     * itself errored on the relative import), the recorded canonical name is
     * just [name].
     *
     * `from m import x` ambiguously binds either a global value or a
     * submodule; without resolution we default to GLOBAL. The resolved case
     * is handled by mypy's `NAME_GLOBAL` fast path in `lowerName`, so this
     * fallback only fires when resolution failed.
     *
     * Same-scope rebind: if the bound name was previously a module binding
     * (e.g. `import x` then `from pkg import x`), the prior module binding
     * is dropped so that [resolve] returns the latest write.
     */
    fun recordImportFrom(module: String, name: String, alias: String) {
        require(name.isNotEmpty()) { "recordImportFrom: `name` must not be empty" }
        val bound = alias.ifEmpty { name }
        val qualified = if (module.isEmpty()) name else "$module.$name"
        modules.remove(bound)
        values[bound] = qualified
    }

    /**
     * Resolve [name] up the scope chain. Module bindings shadow value
     * bindings at the same level (a name can't be both in one scope).
     * Returns null when [name] isn't a recorded import anywhere in the chain;
     * the caller falls back to its default name classification.
     */
    fun resolve(name: String): FlatValue? {
        modules[name]?.let { return FlatModuleRef(it) }
        values[name]?.let { return FlatGlobalRef(it) }
        return parent?.resolve(name)
    }

    /** Construct a manager whose lookups fall through to this one on miss. */
    fun nestedChild(): ImportManager = ImportManager(parent = this)
}
