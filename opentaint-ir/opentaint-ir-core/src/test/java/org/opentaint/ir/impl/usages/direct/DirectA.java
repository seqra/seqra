package org.opentaint.ir.impl.usages.direct;

import com.google.common.collect.Lists;

public class DirectA {

    public static boolean called;

    private boolean result;

    public static void setCalled() {
        called = true;
        System.out.println(called);
    }

    public void newSmth() {
        this.result = Lists.newArrayList().add(1);
        System.out.println(result);
        called = true;
        System.out.println(called);
    }
}
