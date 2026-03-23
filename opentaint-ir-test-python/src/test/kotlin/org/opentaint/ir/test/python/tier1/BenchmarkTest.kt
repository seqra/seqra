package org.opentaint.ir.test.python.tier1

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag

/**
 * Tier 1: Real-world library benchmark tests.
 *
 * Analyzes installed Python packages through the full PIR pipeline.
 * Uses [BenchmarkTestBase] for all assertion and stats logic.
 */
@Tag("tier1")
class BenchmarkTest : BenchmarkTestBase() {

    // ─── Original benchmarks ────────────────────────────────

    @Test @Timeout(600) fun `benchmark - click`() = analyzePkg("click", 5, 5, 20)
    @Test @Timeout(600) fun `benchmark - requests`() = analyzePkg("requests", 5, 5, 20)
    @Test @Timeout(600) fun `benchmark - attrs`() = analyzePkg("attr", 5, 3, 10)
    @Test @Timeout(600) fun `benchmark - typer`() = analyzePkg("typer", 3, 2, 5)
    @Test @Timeout(600) fun `benchmark - rich`() = analyzePkg("rich", 8, 10, 30, recursive = true)
    @Test @Timeout(600) fun `benchmark - pygments`() = analyzePkg("pygments", 7, 8, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - urllib3`() = analyzePkg("urllib3", 6, 5, 15, recursive = true)
    @Test @Timeout(600) fun `benchmark - packaging`() = analyzePkg("packaging", 5, 3, 15)
    @Test @Timeout(600) fun `benchmark - cryptography`() = analyzePkg("cryptography", 6, 1, 10, recursive = true)
    @Test @Timeout(600) fun `benchmark - more-itertools`() = analyzePkg("more_itertools", 2, 1, 5)
    @Test @Timeout(600) fun `benchmark - idna`() = analyzePkg("idna", 4, 2, 5)
    @Test @Timeout(600) fun `benchmark - charset-normalizer`() = analyzePkg("charset_normalizer", 5, 3, 10)
    @Test @Timeout(600) fun `benchmark - markdown-it`() = analyzePkg("markdown_it", 6, 3, 15, recursive = true)
    @Test @Timeout(600) fun `benchmark - grpc`() = analyzePkg("grpc", 6, 5, 15, recursive = true)
    @Test @Timeout(600) fun `benchmark - google-protobuf`() = analyzePkg("google.protobuf", 6, 5, 15, recursive = true)
    @Test @Timeout(600) fun `benchmark - mypy`() = analyzePkg("mypy", 10, 10, 40, recursive = true)
    @Test @Timeout(600) fun `benchmark - shellingham`() = analyzePkg("shellingham", 3, 1, 3)
    @Test @Timeout(600) fun `benchmark - mdurl`() = analyzePkg("mdurl", 3, 1, 5)
    @Test @Timeout(600) fun `benchmark - markupsafe`() = analyzePkg("markupsafe", 1, 1, 2)

    // ─── Web framework libraries ────────────────────────────

    @Test @Timeout(600) fun `benchmark - flask`() = analyzePkg("flask", 10, 5, 30)
    @Test @Timeout(600) fun `benchmark - django`() = analyzePkg("django", 20, 1, 60, recursive = true)
    @Test @Timeout(600) fun `benchmark - fastapi`() = analyzePkg("fastapi", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - starlette`() = analyzePkg("starlette", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - werkzeug`() = analyzePkg("werkzeug", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - jinja2`() = analyzePkg("jinja2", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - tornado`() = analyzePkg("tornado", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - falcon`() = analyzePkg("falcon", 10, 1, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - bottle`() = analyzePkg("bottle", 1, 1, 10)
    @Test @Timeout(600) fun `benchmark - pyramid`() = analyzePkg("pyramid", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - sanic`() = analyzePkg("sanic", 10, 1, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - aiohttp`() = analyzePkg("aiohttp", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - celery`() = analyzePkg("celery", 10, 5, 20, recursive = true)

    // ─── Data / Validation / ORM ────────────────────────────

    @Test @Timeout(600) fun `benchmark - pydantic`() = analyzePkg("pydantic", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - sqlalchemy`() = analyzePkg("sqlalchemy", 15, 20, 60, recursive = true)
    @Test @Timeout(600) fun `benchmark - marshmallow`() = analyzePkg("marshmallow", 6, 3, 10, recursive = true)

    // ─── HTTP clients ───────────────────────────────────────

    @Test @Timeout(600) fun `benchmark - httpx`() = analyzePkg("httpx", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - httpcore`() = analyzePkg("httpcore", 10, 3, 10, recursive = true)

    // ─── Utility libraries ──────────────────────────────────

    @Test @Timeout(600) fun `benchmark - yaml`() = analyzePkg("yaml", 8, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - mako`() = analyzePkg("mako", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - markdown`() = analyzePkg("markdown", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - tqdm`() = analyzePkg("tqdm", 8, 3, 10, recursive = true)
    @Test @Timeout(600) fun `benchmark - pycparser`() = analyzePkg("pycparser", 8, 5, 15, recursive = true)
    @Test @Timeout(600) fun `benchmark - pyparsing`() = analyzePkg("pyparsing", 8, 5, 15, recursive = true)
    @Test @Timeout(600) fun `benchmark - pathspec`() = analyzePkg("pathspec", 8, 3, 10, recursive = true)
    @Test @Timeout(600) fun `benchmark - typeguard`() = analyzePkg("typeguard", 5, 3, 10, recursive = true)
    @Test @Timeout(600) fun `benchmark - anyio`() = analyzePkg("anyio", 10, 5, 20, recursive = true)
    @Test @Timeout(600) fun `benchmark - filelock`() = analyzePkg("filelock", 4, 2, 5, recursive = true)
    @Test @Timeout(600) fun `benchmark - lxml`() = analyzePkg("lxml", 8, 5, 15, recursive = true)
}
