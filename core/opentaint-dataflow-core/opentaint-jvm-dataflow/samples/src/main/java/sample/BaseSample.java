package sample;

public class BaseSample {

    protected Object field;

    public Object getField() {
        return this.field;
    }

    public void setField(Object val) {
        this.field = val;
    }

    public static class Box {
        public Object value;

        public void touchHeap() { }
    }

    public static Object readValue(Box box) {
        return box.value;
    }

    public static Box makeBox(Object value) {
        Box box = new Box();
        box.value = value;
        return box;
    }

    public static Box passThroughBox(Box box) {
        return box;
    }

    public static class Nested {
        public Box box;

        public void touchHeap() { }

        public void touchHeapDepth2() { touchHeap(); }
    }

    public static class Node {
        public Node next;
        public Object data;
    }

    public static Object identity(Object x) {
        return x;
    }

    public static void sinkOneValue(Object v) { }

    public static void sinkTwoValues(Object v1, Object v2) { }

    public static void doNothing() { }
}
