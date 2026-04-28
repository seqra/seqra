package sample.alias;

import sample.AliasSettings;
import sample.BaseSample;

public class CombinedHeapAliasSample extends BaseSample {

    static void writeArgThenTouchHeap(Box box, Object src) {
        box.value = src;
        Object alias = box.value;
        box.touchHeap();
        sinkOneValue(alias);
    }

    @AliasSettings(interProcDepth = 1)
    static void returnArgField(Box box) {
        Object result = readValue(box);
        sinkOneValue(result);
    }

    @AliasSettings(interProcDepth = 1)
    static void returnIdentityThenWriteField(Box box, Object src) {
        Object tmp = identity(src);
        box.value = tmp;
        Object result = box.value;
        sinkOneValue(result);
    }

    @AliasSettings(interProcDepth = 1)
    static void freshObjectCarriesReturnedArg(Object src) {
        Box fresh = makeBox(identity(src));
        Object result = fresh.value;
        sinkOneValue(result);
    }

    static void freshObjectCopiesArgumentField(Box srcBox) {
        Box fresh = new Box();
        fresh.value = srcBox.value;
        Object result = fresh.value;
        sinkOneValue(result);
    }

    @AliasSettings(interProcDepth = 1)
    static void passThroughReceiverThenReadField(Box box) {
        Box alias = passThroughBox(box);
        Object result = alias.value;
        sinkOneValue(result);
    }

    @AliasSettings(interProcDepth = 1)
    static void nestedWriteReturnAndTouchHeap(Nested nested, Object src) {
        nested.box.value = identity(src);
        Object alias = readValue(nested.box);
        nested.touchHeapDepth2();
        sinkOneValue(alias);
    }

    static void overwriteFieldWithFreshObject(Box box, Object src) {
        box.value = src;
        Box fresh = new Box();
        fresh.value = new Object();
        box.value = fresh.value;
        Object result = fresh.value;
        sinkOneValue(result);
    }

    @AliasSettings(interProcDepth = 1)
    static void returnFreshBoxThenAliasField(Box box, Object src) {
        box.value = src;
        Box other = makeBox(box.value);
        Object result = other.value;
        sinkOneValue(result);
    }
}
