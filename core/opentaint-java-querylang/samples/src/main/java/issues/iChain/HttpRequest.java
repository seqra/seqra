package issues.iChain;

/**
 * Stub mirroring `java.net.http.HttpRequest` for the chained-builder
 * pattern repro. The static `newBuilder()` returns a {@link Builder};
 * the builder chain is `.uri(URI).GET().build()`.
 */
public class HttpRequest {
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        public Builder uri(URI uri) {
            return this;
        }

        public Builder GET() {
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest();
        }
    }
}
