package org.opentaint.ir.testing.builders;

import java.util.List;

public class Interfaces {

    public static interface Interface {

    }

    public static class Impl1 implements Interface {
    }

    public static class Impl2 extends Impl1 {
    }

    public Interface build1() {
        return new Impl1();
    }

    public Interface build2(Impl2 impl2) {
        return impl2;
    }

    public Interface build3(List<Impl2> list) {
        return list.get(0);
    }
}
