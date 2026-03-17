package org.opentaint.ir.testing;

public class Bar {
    byte[] byteArray = new byte[19];
    Object[] objectArray = new Object[19];
    Object[][] objectObjectArray = new Object[19][];

    public byte[] smth(byte[] another) {
        return another;
    }

}
