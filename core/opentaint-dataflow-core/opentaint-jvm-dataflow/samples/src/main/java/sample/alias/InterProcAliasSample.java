package sample.alias;

import java.util.Collections;
import java.util.List;

public class InterProcAliasSample {

    Object field;

    Object getField() {
        return this.field;
    }

    void setField(Object val) {
        this.field = val;
    }

    void testGetterAlias() {
        Object result = getField();
        sinkOneValue(result);
    }

    void testSetterThenGetter(Object src) {
        setField(src);
        Object result = getField();
        sinkOneValue(result);
    }

    static Object identity(Object x) {
        return x;
    }

    static void testIdentityCall(Object src) {
        Object result = identity(src);
        sinkOneValue(result);
    }

    static void testExternalCallReturn(Object src) {
        List<Object> list = Collections.singletonList(src);
        sinkOneValue(list);
    }

    static void testExternalCallInvalidatesHeap(Object src) {
        Object[] arr = new Object[1];
        arr[0] = src;
        System.arraycopy(arr, 0, new Object[1], 0, 1);
        Object dst = arr[0];
        sinkOneValue(dst);
    }

    static void sinkOneValue(Object v) { }
}
