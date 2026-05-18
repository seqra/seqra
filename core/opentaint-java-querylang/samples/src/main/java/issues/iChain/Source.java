package issues.iChain;

/**
 * Trivial tainted-source helper. The pattern-source in the rule is
 * `String $UNTRUSTED = Source.taint();`, so any call to `taint()`
 * produces an untrusted string.
 */
public class Source {
    public static String taint() {
        return "tainted";
    }
}
