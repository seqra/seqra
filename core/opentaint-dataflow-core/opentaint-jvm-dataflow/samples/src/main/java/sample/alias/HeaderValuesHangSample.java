package sample.alias;

public final class HeaderValuesHangSample {

    public static void sinkOneValue(Object v) {}

    String[] value;

    public void addAllEntry() {
        ElementBox box = new ElementBox();
        for (int i = 0; i < 1; i++) {
            String headerValue = box.nextItem;
            outOfAnalysisScopeFn1(this.value);
            this.value[0] = headerValue;
        }

        Object out = this;
        sinkOneValue(out);
    }

    public static class ElementBox {
        public String nextItem;
    }

    private static void outOfAnalysisScopeFn0(Object o) {
    }

    private static void outOfAnalysisScopeFn1(Object o) {
        outOfAnalysisScopeFn0(o);
    }
}
