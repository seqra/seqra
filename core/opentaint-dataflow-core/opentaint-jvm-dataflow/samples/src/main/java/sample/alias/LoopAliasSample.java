package sample.alias;

import java.util.List;

public class LoopAliasSample {

    static class Node {
        Node next;
        Object data;
    }

    static void aliasInLoop(Object a, Object b) {
        Object cur = a;
        int i = 0;
        while (i < 10) {
            cur = b;
            i++;
        }
        sinkOneValue(cur);
    }

    static void aliasInForEachLoop(List<Object> list) {
        Object last = null;
        for (Object elem : list) {
            last = elem;
        }
        sinkOneValue(last);
    }

    static void aliasInTryCatch(Object a, Object b) {
        Object result;
        try {
            result = a;
        } catch (Exception e) {
            result = b;
        }
        sinkOneValue(result);
    }

    static void aliasInTryOnly(Object a) {
        Object result = a;
        try {
            doNothing();
        } catch (Exception e) {
        }
        sinkOneValue(result);
    }

    static void nodeNextLoop(Node head) {
        Node cur = head;
        while (cur.next != null) {
            cur = cur.next;
        }
        sinkOneValue(cur);
    }

    static void nodeNextLoopData(Node head) {
        Node cur = head;
        while (cur.next != null) {
            cur = cur.next;
        }
        Object data = cur.data;
        sinkOneValue(data);
    }

    static void sinkOneValue(Object v) { }
    static void doNothing() { }
}
