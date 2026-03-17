package org.opentaint.ir.testing.cfg;

public class NullAssumptionAnalysisExample {

    public void test1(String a) {
        System.out.println("Hello from test1");
        System.out.println(a.length());
    }

    public void test2(Object a) {
        System.out.println("Hello from test2");
        System.out.println(a.hashCode());
        String x = (String) a;
        System.out.println(x.length());
    }
}
