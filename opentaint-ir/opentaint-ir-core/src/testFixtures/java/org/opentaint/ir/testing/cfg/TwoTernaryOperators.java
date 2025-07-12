package org.opentaint.ir.testing.cfg;

public class TwoTernaryOperators {
    @SuppressWarnings("ConstantValue")
    public int f() {
        int i = Integer.parseInt("1");
        boolean b = Boolean.parseBoolean("true");
        return (b ? i : 0) + (!b ? i : 0);
    }

    public String box() {
        int result = f();
        return result == 1 ? "OK" : "BAD";
    }
}
