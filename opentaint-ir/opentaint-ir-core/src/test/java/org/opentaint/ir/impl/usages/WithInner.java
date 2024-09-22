package org.opentaint.ir.impl.usages;

public class WithInner {

    public class Inner {
        public void hello() {
            System.out.println("hello");
        }
    }

    public static class StaticInner {
        public void helloStatic() {
            System.out.println("hello static");
        }
    }

    public void sayHello() {
        new Inner().hello();
        new StaticInner().helloStatic();
        new Runnable() {
            @Override
            public void run() {
                System.out.println("hello anonymous");
            }
        }.run();

    }
}
