package org.opentaint.ir.testing.cfg;

import java.util.function.Function;

public class StaticInterfaceMethodCall {
    public static void callStaticInterfaceMethod() {
        Function.identity();
    }
}