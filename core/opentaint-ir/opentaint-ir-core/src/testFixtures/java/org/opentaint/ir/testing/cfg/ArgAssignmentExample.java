package org.opentaint.ir.testing.cfg;

public class ArgAssignmentExample {
    private static class X {
        public final int a = 0;
    }

    private static X sample(X arg) {
        if (arg.a == 0) {
            arg = null;
        }
        return arg;
    }

    public String box() {
        X result = sample(new X());
        return result == null ? "OK" : "BAD";
    }
}