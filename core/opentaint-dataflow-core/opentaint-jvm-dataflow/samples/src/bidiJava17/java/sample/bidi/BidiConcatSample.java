package sample.bidi;

/** Java 17 emits invokedynamic concat so the real concat transformer is exercised. */
public class BidiConcatSample {
    public static String concat(String value, String suffix) {
        return value + suffix;
    }
}
