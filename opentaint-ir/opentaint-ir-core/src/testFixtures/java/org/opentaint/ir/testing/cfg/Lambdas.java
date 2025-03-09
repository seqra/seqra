package org.opentaint.ir.testing.cfg;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Lambdas {

    public static Object lambdaTest() {
        List<Integer> l = Arrays.asList(543, 432, 1, -23);
        l.sort((integer, t1) -> integer.compareTo(t1));
        return null;
    }
}
