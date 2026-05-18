package issues.iChain;

/**
 * Stub mirroring `java.net.http.HttpClient` — the sink consumes a
 * built {@link HttpRequest}.
 */
public class HttpClient {
    public static HttpClient newHttpClient() {
        return new HttpClient();
    }

    public String send(HttpRequest req) {
        return "response";
    }
}
