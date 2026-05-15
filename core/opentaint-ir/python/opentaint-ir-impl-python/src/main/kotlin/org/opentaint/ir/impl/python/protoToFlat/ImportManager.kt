package org.opentaint.ir.impl.python.protoToFlat

/**
 * Recursive descriptor of a resolved import binding, returned by
 * [ImportManager.resolve]. Every dotted path is pre-built into a chain
 * of single-segment nodes at recording time so the lowering site never
 * has to parse a string.
 *
 * - [Module]: root of the chain. Names a top-level module by a single
 *   segment (e.g. `os`, `collections`). The lowering materializes this
 *   as `FlatModuleRef(name)` with no instructions emitted.
 * - [Attr]: attribute-of-binding link. The attribute may be a submodule
 *   (`os.path` is `Attr(Module(os), "path")`) or a value imported from a
 *   module (`from os import getcwd` is `Attr(Module(os), "getcwd")`).
 *   The lowering materializes this as a `FlatLoadAttr` off the
 *   materialized parent. Submodule-vs-value is intentionally unmarked —
 *   the IR shape is the same either way, matching Python's
 *   `LOAD_ATTR`-on-module runtime semantics.
 * - [BareGlobal]: fallback for `from . import x` where mypy itself
 *   couldn't resolve the relative-import base. There's no module object
 *   to attribute off of, so the lowering produces `FlatGlobalRef(name)`.
 *
 * Worked examples:
 *
 * - `import os`                          → `Module("os")`
 * - `import os.path`                     → `Module("os")` (only root bound)
 * - `import os.path as p`                → `Attr(Module("os"), "path")`
 * - `from os import getcwd`              → `Attr(Module("os"), "getcwd")`
 * - `from collections.abc import It`     → `Attr(Attr(Module("collections"), "abc"), "It")`
 */
internal sealed interface ImportBinding {
    data class Module(val name: String) : ImportBinding {
        init {
            require('.' !in name) { "Module.name must be a single segment, got '$name'" }
        }
    }
    data class Attr(val parent: ImportBinding, val name: String) : ImportBinding {
        init {
            require('.' !in name) { "Attr.name must be a single segment, got '$name'" }
        }
    }
    data class BareGlobal(val name: String) : ImportBinding
}

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
 * A single bound name is either a module *or* a value, never both — Python's
 * "textually-last write wins" rule for same-scope rebinds is enforced by
 * the single [bindings] map: every record overwrites the previous entry
 * under the same key.
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
 *
 * The manager itself does not produce `FlatValue` instances — it returns a
 * recursive [ImportBinding] descriptor. The lowering site materializes the
 * binding into IR (a `FlatModuleRef` rooted chain of `FlatLoadAttr`s).
 * Keeping that materialization outside the manager preserves the
 * cross-module invariant: every cross-module reference appears in the
 * instruction stream as an explicit attribute read, not as a
 * `FlatGlobalRef` carrying a dotted foreign fullname.
 */
internal class ImportManager(private val parent: ImportManager? = null) {

    private val bindings = mutableMapOf<String, ImportBinding>()

    /**
     * Record one entry of an `import` statement, given its written-source
     * `module` (e.g. `m.sub`) and optional `alias` (`""` when absent).
     * Derives the bound name and canonical binding per Python's runtime
     * rules:
     *
     * - `import m`          → bound=`m`,  binding=`Module(m)`
     * - `import m.sub`      → bound=`m`,  binding=`Module(m)`       — only
     *                                                                 the root
     *                                                                 is bound
     * - `import m as a`     → bound=`a`,  binding=`Module(m)`
     * - `import m.sub as a` → bound=`a`,  binding=`Attr(Module(m), sub)` —
     *                                                                 alias
     *                                                                 names the
     *                                                                 deep
     *                                                                 module
     *
     * Any prior binding under the same name is dropped — Python's "the
     * textually-last binding wins within a scope" rule.
     */
    fun recordImport(module: String, alias: String) {
        val bound: String
        val binding: ImportBinding
        if (alias.isNotEmpty()) {
            bound = alias
            binding = moduleChain(module)
        } else {
            // No alias: only the root segment becomes a name; the deeper
            // module is reached at runtime via attribute access on that root.
            bound = module.substringBefore('.')
            binding = ImportBinding.Module(bound)
        }
        bindings[bound] = binding
    }

    /**
     * Record one entry of a `from m.sub import x [as y]` statement.
     *
     * [module] is the ALREADY-RESOLVED absolute module path — relative
     * imports must be resolved upstream (currently in `ast_serializer.py` via
     * `mypy.util.correct_relative_import`). When [module] is empty (mypy
     * itself errored on the relative import), the binding is a
     * [ImportBinding.BareGlobal] — downstream materialization falls back
     * to `FlatGlobalRef(name)` for that case (no module object to
     * attribute off of).
     *
     * `from m import x` ambiguously binds either a global value or a
     * submodule; without resolution we default to an [ImportBinding.Attr]
     * over the module chain — the correct shape for the value case, and
     * a faithful representation of the submodule case (attribute-of-module
     * access at runtime).
     *
     * Any prior binding under the same name is dropped — last write wins.
     */
    fun recordImportFrom(module: String, name: String, alias: String) {
        require(name.isNotEmpty()) { "recordImportFrom: `name` must not be empty" }
        val bound = alias.ifEmpty { name }
        bindings[bound] = if (module.isEmpty()) {
            ImportBinding.BareGlobal(name)
        } else {
            ImportBinding.Attr(moduleChain(module), name)
        }
    }

    /**
     * Resolve [name] up the scope chain. Returns null when [name] isn't a
     * recorded import anywhere in the chain; the caller falls back to its
     * default name classification.
     */
    fun resolve(name: String): ImportBinding? {
        bindings[name]?.let { return it }
        return parent?.resolve(name)
    }

    /** Construct a manager whose lookups fall through to this one on miss. */
    fun nestedChild(): ImportManager = ImportManager(parent = this)
}

internal fun moduleChain(dottedPath: String): ImportBinding {
    require(dottedPath.isNotEmpty()) {
        "Dotted path must be non-empty"
    }

    val segments = dottedPath.split(".")
    var node: ImportBinding = ImportBinding.Module(segments.first())
    for (i in 1 until segments.size) {
        node = ImportBinding.Attr(node, segments[i])
    }
    return node
}
