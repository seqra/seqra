package org.opentaint.ir.testing.cfg;

public class Incrementation {

    static public int iinc(int x) {
        return x++;
    }

    static public int[] iincArrayIntIdx() {
        int[] arr = new int[3];
        int idx = 0;
        arr[idx++] = 1;
        arr[++idx] = 2;
        return arr;
    }

    static public int[] iincArrayByteIdx() {
        int[] arr = new int[3];
        byte idx = 0;
        arr[idx++] = 1;
        arr[++idx] = 2;
        return arr;
    }

    static public int[] iincFor() {
        int[] result = new int[5];
        for (int i = 0; i < 5; i++) {
            result[i] = i;
        }
        return result;
    }

    static public int[] iincIf(boolean x, boolean y) {
        int xx = 0;
        if (x != y) {
            xx++;
        }
        return new int[xx];
    }

    static public int iincWhile() {
        int x = 0;
        int y = 0;
        while (x++ < 2) {
            y++;
        }
        return y;
    }

    static public int iincIf2(int x) {
        if (x++ == 1) {
            return x;
        }
        return x + 1;
    }

    public static String iincCustomWhile() {
        int x = 0;

        while(x++ < 5) {
        }

        return x != 6 ? "Fail: " + x : "OK";
    }

}
