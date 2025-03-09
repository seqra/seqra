package org.opentaint.ir.testing.cfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

    public static void conditionInFor() {
        Random rnd = new Random();
        List<Boolean> list = new ArrayList<>();
        int numFalse = 0;
        for (int i = 0; i < 1000; i++) {
            boolean element = rnd.nextBoolean();
            if (!element)
                numFalse++;
            list.add(element);
        }

        Collections.sort(list);

        for (int i = 0; i < numFalse; i++)
            if (list.get(i))
                throw new RuntimeException("False positive: " + i);
        for (int i = numFalse; i < 1000; i++)
            if (!list.get(i))
                throw new RuntimeException("False negative: " + i);
    }

}
