package org.opentaint.ir.testing.cfg;

public class Conditionals {
    void main(int x, int y) {
        if (x < 0) {
            System.out.println("< 0");
        }
        if (x <= 0) {
            System.out.println("<= 0");
        }
        if (x < y) {
            System.out.println("<");
        }
        if (x <= y) {
            System.out.println("<=");
        }
    }
}
