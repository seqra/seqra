package org.opentaint.ir.test.python.tier1

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag

/**
 * Tier 1: Real-world web application benchmarks.
 *
 * Analyzes cloned web application projects (Django, Flask, FastAPI, etc.)
 * through the full PIR pipeline. Uses the same strict assertions as
 * [BenchmarkTest] via [BenchmarkTestBase].
 */
@Tag("tier1")
class WebAppBenchmarkTest : BenchmarkTestBase() {

    companion object {
        private const val WEB_PROJECTS_DIR = "/home/sobol/data/python-ir/web-projects"
    }

    // ─── Django Web Applications ─────────────────────────────

    @Test @Timeout(600) fun `webapp - saleor (Django e-commerce)`() =
        analyzeDir("saleor", "$WEB_PROJECTS_DIR/saleor/saleor", 20, 1, 30)

    @Test @Timeout(600) fun `webapp - netbox (Django network automation)`() =
        analyzeDir("netbox", "$WEB_PROJECTS_DIR/netbox/netbox", 20, 1, 30,
            expectedUnknownModules = setOf("__build_errors__"))

    @Test @Timeout(600) fun `webapp - wagtail (Django CMS)`() =
        analyzeDir("wagtail", "$WEB_PROJECTS_DIR/wagtail/wagtail", 20, 1, 30)

    @Test @Timeout(600) fun `webapp - taiga-back (Django project management)`() =
        analyzeDir("taiga-back", "$WEB_PROJECTS_DIR/taiga-back/taiga", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - django-oscar (Django e-commerce)`() =
        analyzeDir("django-oscar", "$WEB_PROJECTS_DIR/django-oscar/src", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - django-rest-framework`() =
        analyzeDir("django-rest-framework", "$WEB_PROJECTS_DIR/django-rest-framework/rest_framework", 10, 5, 20)

    @Test @Timeout(600) fun `webapp - healthchecks (Django monitoring)`() =
        analyzeDir("healthchecks", "$WEB_PROJECTS_DIR/healthchecks/hc", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - zulip (Django chat)`() =
        analyzeDir("zulip", "$WEB_PROJECTS_DIR/zulip/zerver", 20, 1, 30)

    @Test @Timeout(600) fun `webapp - label-studio (Django data labeling)`() =
        analyzeDir("label-studio", "$WEB_PROJECTS_DIR/label-studio/label_studio", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - paperless-ngx (Django document management)`() =
        analyzeDir("paperless-ngx", "$WEB_PROJECTS_DIR/paperless-ngx/src", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - flagsmith (Django feature flags)`() =
        analyzeDir("flagsmith", "$WEB_PROJECTS_DIR/flagsmith/api", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - hyperkitty (Django mailing lists)`() =
        analyzeDir("hyperkitty", "$WEB_PROJECTS_DIR/hyperkitty/hyperkitty", 10, 1, 20)

    @Test @Timeout(600) fun `webapp - ralph (Django asset management)`() =
        analyzeDir("ralph", "$WEB_PROJECTS_DIR/ralph/src/ralph", 10, 0, 20,
            expectedUnknownModules = setOf("__build_errors__"))

    @Test @Timeout(600) fun `webapp - plane (Django project tracker)`() =
        analyzeDir("plane", "$WEB_PROJECTS_DIR/plane/apps", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - posthog (Django analytics)`() =
        analyzeDir("posthog", "$WEB_PROJECTS_DIR/posthog/posthog", 20, 0, 30)

    @Test @Timeout(600) fun `webapp - sentry (Django error tracking)`() =
        analyzeDir("sentry", "$WEB_PROJECTS_DIR/sentry/src", 20, 1, 30)

    // ─── Flask Web Applications ──────────────────────────────

    @Test @Timeout(600) fun `webapp - superset (Flask BI platform)`() =
        analyzeDir("superset", "$WEB_PROJECTS_DIR/superset/superset", 20, 0, 30)

    @Test @Timeout(600) fun `webapp - redash (Flask dashboarding)`() =
        analyzeDir("redash", "$WEB_PROJECTS_DIR/redash/redash", 10, 1, 20)

    @Test @Timeout(600) fun `webapp - CTFd (Flask CTF platform)`() =
        analyzeDir("CTFd", "$WEB_PROJECTS_DIR/CTFd/CTFd", 10, 1, 20)

    @Test @Timeout(600) fun `webapp - flaskbb (Flask forum)`() =
        analyzeDir("flaskbb", "$WEB_PROJECTS_DIR/flaskbb/flaskbb", 10, 1, 20)

    @Test @Timeout(600) fun `webapp - lemur (Flask certificate manager)`() =
        analyzeDir("lemur", "$WEB_PROJECTS_DIR/lemur/lemur", 10, 0, 20)

    // ─── FastAPI Web Applications ────────────────────────────

    @Test @Timeout(600) fun `webapp - mealie (FastAPI recipe manager)`() =
        analyzeDir("mealie", "$WEB_PROJECTS_DIR/mealie/mealie", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - dispatch (FastAPI incident management)`() =
        analyzeDir("dispatch", "$WEB_PROJECTS_DIR/dispatch/src/dispatch", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - polar (FastAPI billing)`() =
        analyzeDir("polar", "$WEB_PROJECTS_DIR/polar/server/polar", 10, 0, 20)

    @Test @Timeout(600) fun `webapp - full-stack-fastapi-template`() =
        analyzeDir("fastapi-template", "$WEB_PROJECTS_DIR/full-stack-fastapi-template/backend/app", 5, 1, 5)
}
