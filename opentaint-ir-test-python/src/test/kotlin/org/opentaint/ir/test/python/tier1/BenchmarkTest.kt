package org.opentaint.ir.test.python.tier1

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag

/**
 * Tier 1: Real-world library benchmark tests.
 *
 * Analyzes installed Python packages through the full PIR pipeline.
 * Uses [BenchmarkTestBase] for all assertion and stats logic.
 * Entity counts are exact expected values (modules, classes, functions).
 */
@Tag("tier1")
class BenchmarkTest : BenchmarkTestBase() {

    // ─── Original benchmarks ────────────────────────────────

    @Test @Timeout(600) fun `benchmark - click`() = analyzePkg("click", 17, 67, 506)
    @Test @Timeout(600) fun `benchmark - requests`() = analyzePkg("requests", 18, 44, 257)
    @Test @Timeout(600) fun `benchmark - attrs`() = analyzePkg("attr", 13, 35, 227)
    @Test @Timeout(600) fun `benchmark - typer`() = analyzePkg("typer", 16, 36, 185)
    @Test @Timeout(600) fun `benchmark - rich`() = analyzePkg("rich", 100, 173, 1053, recursive = true)
    @Test @Timeout(600) fun `benchmark - pygments`() = analyzePkg("pygments", 338, 752, 1246, recursive = true)
    @Test @Timeout(600) fun `benchmark - urllib3`() = analyzePkg("urllib3", 36, 96, 516, recursive = true)
    @Test @Timeout(600) fun `benchmark - packaging`() = analyzePkg("packaging", 15, 55, 322)
    @Test @Timeout(600) fun `benchmark - cryptography`() = analyzePkg("cryptography", 72, 262, 1025, recursive = true)
    @Test @Timeout(600) fun `benchmark - more-itertools`() = analyzePkg("more_itertools", 3, 12, 263)
    @Test @Timeout(600) fun `benchmark - idna`() = analyzePkg("idna", 8, 9, 122)
    @Test @Timeout(600) fun `benchmark - charset-normalizer`() = analyzePkg("charset_normalizer", 10, 13, 145)
    @Test @Timeout(600) fun `benchmark - markdown-it`() = analyzePkg("markdown_it", 66, 24, 325, recursive = true)
    @Test @Timeout(600) fun `benchmark - grpc`() = analyzePkg("grpc", 56, 203, 1087, recursive = true)
    @Test @Timeout(600) fun `benchmark - google-protobuf`() = analyzePkg("google.protobuf", 54, 79, 890, recursive = true)
    @Test @Timeout(600) fun `benchmark - mypy`() = analyzePkg("mypy", 187, 519, 6503, recursive = true)
    @Test @Timeout(600) fun `benchmark - shellingham`() = analyzePkg("shellingham", 3, 2, 12)
    @Test @Timeout(600) fun `benchmark - mdurl`() = analyzePkg("mdurl", 6, 2, 16)
    @Test @Timeout(600) fun `benchmark - markupsafe`() = analyzePkg("markupsafe", 2, 5, 56)

    // ─── Web framework libraries ────────────────────────────

    @Test @Timeout(600) fun `benchmark - flask`() = analyzePkg("flask", 18, 30, 228)
    @Test @Timeout(600) fun `benchmark - django`() = analyzePkg("django", 899, 1907, 10298, recursive = true)
    @Test @Timeout(600) fun `benchmark - fastapi`() = analyzePkg("fastapi", 48, 99, 476, recursive = true)
    @Test @Timeout(600) fun `benchmark - starlette`() = analyzePkg("starlette", 34, 104, 533, recursive = true)
    @Test @Timeout(600) fun `benchmark - werkzeug`() = analyzePkg("werkzeug", 52, 177, 1176, recursive = true)
    @Test @Timeout(600) fun `benchmark - jinja2`() = analyzePkg("jinja2", 25, 152, 796, recursive = true)
    @Test @Timeout(600) fun `benchmark - tornado`() = analyzePkg("tornado", 73, 544, 3004, recursive = true)
    @Test @Timeout(600) fun `benchmark - falcon`() = analyzePkg("falcon", 102, 223, 1044, recursive = true)
    @Test @Timeout(600) fun `benchmark - bottle`() = analyzePkg("bottle", 4, 104, 587)
    @Test @Timeout(600) fun `benchmark - pyramid`() = analyzePkg("pyramid", 61, 288, 1104, recursive = true)
    @Test @Timeout(600) fun `benchmark - sanic`() = analyzePkg("sanic", 132, 198, 1169, recursive = true)
    @Test @Timeout(600) fun `benchmark - aiohttp`() = analyzePkg("aiohttp", 55, 306, 1647, recursive = true)
    @Test @Timeout(600) fun `benchmark - celery`() = analyzePkg("celery", 161, 276, 3112, recursive = true)

    // ─── Data / Validation / ORM ────────────────────────────

    @Test @Timeout(600) fun `benchmark - pydantic`() = analyzePkg("pydantic", 105, 358, 1922, recursive = true)
    @Test @Timeout(600) fun `benchmark - sqlalchemy`() = analyzePkg("sqlalchemy", 256, 1723, 10777, recursive = true)
    @Test @Timeout(600) fun `benchmark - marshmallow`() = analyzePkg("marshmallow", 14, 63, 245, recursive = true)

    // ─── HTTP clients ───────────────────────────────────────

    @Test @Timeout(600) fun `benchmark - httpx`() = analyzePkg("httpx", 23, 85, 465, recursive = true)
    @Test @Timeout(600) fun `benchmark - httpcore`() = analyzePkg("httpcore", 31, 85, 454, recursive = true)

    // ─── Utility libraries ──────────────────────────────────

    @Test @Timeout(600) fun `benchmark - yaml`() = analyzePkg("yaml", 17, 88, 364, recursive = true)
    @Test @Timeout(600) fun `benchmark - mako`() = analyzePkg("mako", 33, 91, 575, recursive = true)
    @Test @Timeout(600) fun `benchmark - markdown`() = analyzePkg("markdown", 33, 110, 424, recursive = true)
    @Test @Timeout(600) fun `benchmark - tqdm`() = analyzePkg("tqdm", 31, 37, 269, recursive = true)
    @Test @Timeout(600) fun `benchmark - pycparser`() = analyzePkg("pycparser", 17, 83, 673, recursive = true)
    @Test @Timeout(600) fun `benchmark - pyparsing`() = analyzePkg("pyparsing", 17, 87, 524, recursive = true)
    @Test @Timeout(600) fun `benchmark - pathspec`() = analyzePkg("pathspec", 31, 26, 142, recursive = true)
    @Test @Timeout(600) fun `benchmark - typeguard`() = analyzePkg("typeguard", 12, 17, 146, recursive = true)
    @Test @Timeout(600) fun `benchmark - anyio`() = analyzePkg("anyio", 42, 163, 1130, recursive = true)
    @Test @Timeout(600) fun `benchmark - filelock`() = analyzePkg("filelock", 10, 18, 91, recursive = true)
    @Test @Timeout(600) fun `benchmark - lxml`() = analyzePkg("lxml", 25, 52, 419, recursive = true)
}
