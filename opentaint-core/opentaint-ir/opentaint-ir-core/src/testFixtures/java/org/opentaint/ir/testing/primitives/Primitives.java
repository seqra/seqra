package org.opentaint.ir.testing.primitives;

public class Primitives {

    public int example(short s, char c) {
        return s + c;
    }

    public int example(byte s, short c) {
        return s + c;
    }

    public long example(int s, long c) {
        return s + c;
    }

    public float example(int s, float c) {
        return s + c;
    }

    public int example(char s, char c) {
        return s + c;
    }

    public int unaryExample(char a) {
        return -a;
    }

    public int unaryExample(byte a) {
        return -a;
    }

    public int unaryExample(short a) {
        return -a;
    }

    public long unaryExample(long a) {
        return -a;
    }

    public float unaryExample(float a) {
        return -a;
    }
}
