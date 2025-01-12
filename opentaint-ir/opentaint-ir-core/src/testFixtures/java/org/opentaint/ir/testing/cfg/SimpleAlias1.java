package org.opentaint.ir.testing.cfg;

/*
 * @testcase SimpleAlias1
 *
 * @version 1.0
 *
 * @author Johannes Späth, Nguyen Quang Do Lisa (Secure Software Engineering Group, Fraunhofer
 * Institute SIT)
 *
 * @description Direct alias
 */
public class SimpleAlias1 {

    public static void main(String[] args) {
        Benchmark.alloc(1);
        A a = (A) Benchmark.taint(); //new A();
        A b = new A(); // Added to avoid cfg optimizations
        Benchmark.use(b);
        b = a;
        Benchmark.use(b);
        Benchmark.use(a);
        Benchmark.test("b",
                "{allocId:1, mayAlias:[a,b], notMayAlias:[], mustAlias:[a,b], notMustAlias:[]}");
    }
}

class A {

    // Object A with attributes of type B

    public int i = 5;

}

class Benchmark {

    public static void alloc(int id) {

    }

    public static void test(String targetVariable, String results) {

    }

    public static void use(Object o) {
        o.hashCode();
        //A method to be used to avoid the compiler to prune the Object
    }

    public static Object taint() {
        return new Object();
    }
}
