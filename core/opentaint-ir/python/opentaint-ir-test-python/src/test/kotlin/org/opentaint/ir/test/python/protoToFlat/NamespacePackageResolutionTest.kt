package org.opentaint.ir.test.python.protoToFlat

import org.junit.jupiter.api.Test
import org.opentaint.ir.api.python.PIRSettings
import org.opentaint.ir.impl.python.flat.FlatCall
import org.opentaint.ir.impl.python.flat.FlatFunctionIR
import org.opentaint.ir.impl.python.flat.FlatModuleIR
import java.io.File
import java.nio.file.Files
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end check that PEP 420 namespace packages reachable via
 * [PIRSettings.searchPaths] + `--namespace-packages` / `--explicit-package-bases`
 * are actually resolved by mypy.
 *
 * Discriminator: [FlatCall.resolvedCallee]. Mypy sets it from its name-resolution
 * pass — if it couldn't resolve the import target, the call carries no fullname
 * and `resolvedCallee` is null. This is independent of how the import expression
 * itself surfaces (which may end up as a `FlatModuleRef` either way thanks to
 * the suppressed-import classification fallback).
 *
 * The fixture mirrors the OWASP Benchmark layout: a `helpers/` directory
 * without `__init__.py`, with a sibling `main.py` that imports it. The helper
 * file is deliberately omitted from `sources` so mypy must locate it via
 * `mypy_path` — which only works when `searchPaths` is actually applied on the
 * server.
 */
class NamespacePackageResolutionTest {

    private data class Fixture(val mainPath: String, val helperPath: String, val rootDir: String)

    private fun makeFixture(): Fixture {
        val tmpDir = Files.createTempDirectory("ns-pkg-test").toFile()
        tmpDir.deleteOnExit()

        val helpersDir = File(tmpDir, "helpers")
        helpersDir.mkdir()
        helpersDir.deleteOnExit()

        // PEP 420 namespace package: no __init__.py in helpersDir.
        val dbSqlite = File(helpersDir, "db_sqlite.py")
        dbSqlite.writeText(
            """
                def run_query(q):
                    return q
            """.trimIndent(),
        )
        dbSqlite.deleteOnExit()

        val main = File(tmpDir, "main.py")
        main.writeText(
            """
                import helpers.db_sqlite

                def f(x):
                    return helpers.db_sqlite.run_query(x)
            """.trimIndent(),
        )
        main.deleteOnExit()

        return Fixture(main.absolutePath, dbSqlite.absolutePath, tmpDir.absolutePath)
    }

    private fun resolvedCalleeOfRunQueryIn(fn: FlatFunctionIR): String? {
        for (block in fn.cfg.blocks) {
            for (inst in block.instructions) {
                if (inst is FlatCall) {
                    val resolved = inst.resolvedCallee ?: continue
                    if (resolved.endsWith("run_query")) return resolved
                }
            }
        }
        return null
    }

    private fun loadMain(settings: PIRSettings): FlatFunctionIR {
        val modules = PIRRawFlatLoader.loadRawFlatModules(settings)
        val mainModule: FlatModuleIR = modules.first { it.moduleName == "main" }
        return mainModule.functions.first { it.qualifiedName.endsWith(".f") }
    }

    @Test
    fun `namespace package call resolves through searchPaths + namespace flags`() {
        val fx = makeFixture()

        val f = loadMain(
            PIRSettings(
                // helper file deliberately NOT in sources — must be found via mypy_path
                sources = listOf(fx.mainPath),
                mypyFlags = listOf(
                    "--ignore-missing-imports",
                    "--namespace-packages",
                    "--explicit-package-bases",
                ),
                searchPaths = listOf(fx.rootDir),
            ),
        )

        val resolved = resolvedCalleeOfRunQueryIn(f)
        assertNotNull(
            resolved,
            "Expected mypy to resolve helpers.db_sqlite.run_query when searchPaths " +
                "+ namespace flags are set, but FlatCall.resolvedCallee was null.",
        )
        assertTrue(
            resolved.contains("helpers.db_sqlite") && resolved.endsWith("run_query"),
            "Expected resolvedCallee to point into helpers.db_sqlite, got: $resolved",
        )
    }

    @Test
    fun `namespace package call is unresolved without searchPaths`() {
        val fx = makeFixture()

        val f = loadMain(
            PIRSettings(
                sources = listOf(fx.mainPath),
                mypyFlags = listOf(
                    "--ignore-missing-imports",
                    "--namespace-packages",
                    "--explicit-package-bases",
                ),
                // no searchPaths — mypy cannot find helpers/
                searchPaths = emptyList(),
            ),
        )

        assertNull(
            resolvedCalleeOfRunQueryIn(f),
            "Without searchPaths, mypy should not be able to resolve " +
                "helpers.db_sqlite.run_query — expected resolvedCallee to be null.",
        )
    }

    @Test
    fun `namespace package call is unresolved without namespace flags`() {
        val fx = makeFixture()

        val f = loadMain(
            PIRSettings(
                sources = listOf(fx.mainPath),
                // no --namespace-packages / --explicit-package-bases
                mypyFlags = listOf("--ignore-missing-imports"),
                searchPaths = listOf(fx.rootDir),
            ),
        )

        assertNull(
            resolvedCalleeOfRunQueryIn(f),
            "Without --namespace-packages, mypy should not treat helpers/ as a " +
                "PEP 420 package — expected resolvedCallee to be null.",
        )
    }
}
