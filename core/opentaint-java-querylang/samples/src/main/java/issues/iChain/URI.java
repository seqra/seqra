package issues.iChain;

/**
 * Stub mirroring `java.net.URI` for the chained-builder pattern repro.
 * Real `java.net.URI` would also work but using a stub keeps the test
 * self-contained.
 */
public class URI {
    public final String value;

    private URI(String value) {
        this.value = value;
    }

    public static URI create(String s) {
        return new URI(s);
    }
}
