package issues;

import base.RuleSample;
import base.RuleSet;
import issues.iChain.HttpClient;
import issues.iChain.HttpRequest;
import issues.iChain.Source;
import issues.iChain.URI;

/**
 * Reproducer: a sink-side pattern that names a static-method receiver
 * literally (`HttpRequest.newBuilder().uri($URL)`) does NOT match a
 * chained call expression like
 *   `req = HttpRequest.newBuilder().uri(URI.create(t)).GET().build();`
 *
 * Replacing the literal receiver with `$_.uri($URL)` matches the same
 * shape (see SSRF rule `ssrf-sinks.yaml` for the production version
 * that uses the `$_` form). The repro contrasts the two forms in
 * separate rule files; the YAML referenced by `@RuleSet` is the
 * failing literal-receiver form.
 *
 * The PositiveTaint case exercises the same shape that the production
 * `UnsafeHttpClientSendController` test uses; this test fails until
 * the literal-receiver form is supported (or the documented
 * `(HttpRequest.Builder $_)` typed-receiver constraint becomes
 * available).
 */
@RuleSet("issues/issueChain.yaml")
public abstract class issueChain implements RuleSample {

    static class PositiveTaint extends issueChain {
        @Override
        public void entrypoint() {
            String t = Source.taint();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(t))
                    .GET()
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            client.send(req);
        }
    }

    static class NegativeTaint extends issueChain {
        @Override
        public void entrypoint() {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://example.com"))
                    .GET()
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            client.send(req);
        }
    }
}
