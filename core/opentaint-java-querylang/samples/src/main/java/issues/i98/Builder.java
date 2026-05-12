package issues.i98;

public class Builder {
    public Builder cfg(String key, String value) { return this; }

    public void sink(String taint) { }

    public static String source() { return "oops"; }
}
