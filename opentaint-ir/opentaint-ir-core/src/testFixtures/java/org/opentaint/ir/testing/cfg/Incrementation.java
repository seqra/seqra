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

}
