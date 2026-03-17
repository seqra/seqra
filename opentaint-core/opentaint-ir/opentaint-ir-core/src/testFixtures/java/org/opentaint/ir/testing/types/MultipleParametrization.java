package org.opentaint.ir.testing.types;

import java.util.ArrayList;
import java.util.List;

public class MultipleParametrization {

    public static class SuperTest1<T, W extends List<T>, Z extends List<W>> {
        T stateT;
        W stateW;
        Z stateZ;

        T runT(T in) {
            return null;
        }

        W runW(W in) {
            return null;
        }

        Z runZ(Z in) {
            return null;
        }
    }

    public static class SuperTest2<W extends List<String>, Z extends List<W>> extends SuperTest1<String, W, Z> {
    }

    public static class SuperTest3 extends SuperTest2<ArrayList<String>, ArrayList<ArrayList<String>>> {
    }

}
