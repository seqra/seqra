package sample.alias;

public class HeapAliasSample {

    static class Box {
        Object value;
    }

    static class Nested {
        Box box;
    }

    static class Node {
        Node next;
        Object data;
    }

    static void readArgField(Box box) {
        Object dst = box.value;
        sinkOneValue(dst);
    }

    static void writeArgField(Box box, Object src) {
        box.value = src;
        Object dst = box.value;
        sinkOneValue(dst);
    }

    static void readArgDeepField(Nested nested) {
        Object dst = nested.box.value;
        sinkOneValue(dst);
    }

    static void writeArgDeepField(Nested nested, Object src) {
        nested.box.value = src;
        Object dst = nested.box.value;
        sinkOneValue(dst);
    }

    static void readArgArrayElement(Object[] arr) {
        Object dst = arr[0];
        sinkOneValue(dst);
    }

    static void writeArgArrayElement(Object[] arr, Object src) {
        arr[0] = src;
        Object dst = arr[0];
        sinkOneValue(dst);
    }

    static void fieldToField(Box src, Box dst) {
        dst.value = src.value;
        Object result = dst.value;
        sinkOneValue(result);
    }

    static void swapFields(Box a, Box b) {
        Object tmp = a.value;
        a.value = b.value;
        b.value = tmp;
        sinkTwoValues(a.value, b.value);
    }

    static void arrayToField(Object[] arr, Box box) {
        box.value = arr[0];
        Object dst = box.value;
        sinkOneValue(dst);
    }

    static void fieldToArray(Box box, Object[] arr) {
        arr[0] = box.value;
        Object dst = arr[0];
        sinkOneValue(dst);
    }

    static void nodeTraversal(Node node) {
        Node cur = node;
        while (cur.next != null) {
            cur = cur.next;
        }
        sinkOneValue(cur);
    }

    static void nodeTraversalData(Node node) {
        Node cur = node;
        while (cur.next != null) {
            cur = cur.next;
        }
        Object data = cur.data;
        sinkOneValue(data);
    }

    static void fieldOverwrite(Box box, Object a, Object b) {
        box.value = a;
        box.value = b;
        Object dst = box.value;
        sinkOneValue(dst);
    }

    static void conditionalFieldWrite(Box box, Object a, Object b, boolean cond) {
        if (cond) {
            box.value = a;
        } else {
            box.value = b;
        }
        Object dst = box.value;
        sinkOneValue(dst);
    }

    static void aliasedReceiverFieldWrite(Box b1, Box b2, Object src) {
        b1.value = src;
        Object dst = b2.value;
        sinkOneValue(dst);
    }

    static void sinkOneValue(Object v) { }
    static void sinkTwoValues(Object v1, Object v2) { }
}
