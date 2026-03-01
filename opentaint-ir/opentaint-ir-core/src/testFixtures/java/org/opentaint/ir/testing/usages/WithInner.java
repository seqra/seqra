package org.opentaint.ir.testing.usages;

import java.util.Collection;
import java.util.List;

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

    public static void main(String[] args) {
        new WithInner1<List<String>>();
    }
}


class WithInner1<T extends Collection<?>> {
    public static void main(String[] args) {
        new WithInner1<List<String>>();
    }
}
