package org.opentaint.ir.testing.cfg;

public class JavaArrays {

    public static void arrayClone() {
        byte[] x = new byte[1];
        System.out.println(x.clone().length);
    }

    public static void arrayObjectMethods() {
        byte[] x = new byte[1];
        x.notify();
        System.out.println(x.clone().length);
    }

    public static void arrayObjectMonitors() {
        byte[] x = new byte[1];
        synchronized (x) {
            System.out.println(x.length);
        }
    }
}
