package issues.i103;

public class Builder {
    public static Builder make() { return new Builder(); }
    public Builder safe(String ct) { return this; }
    public Object body(Object o) { return o; }
}
