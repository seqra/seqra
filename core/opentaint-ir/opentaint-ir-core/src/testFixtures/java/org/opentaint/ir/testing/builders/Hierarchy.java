package org.opentaint.ir.testing.builders;

public class Hierarchy {

    public interface HierarchyInterface {
    }

    public static class HierarchyImpl1 implements HierarchyInterface {
    }

    public HierarchyImpl1 build() {
        return new HierarchyImpl1();
    }
}
