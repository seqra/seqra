package test.samples.bidi;

/** Real bytecode programs. Rule configuration lives in the Kotlin acceptance test. */
public class BidiAcceptanceSample {
    public static class Dto {
        public Object name;
        public Object email;
        public Object k;
        public Dto next;
    }

    public static Object getter(Dto dto) { return dto.email; }
    public static Dto identity(Dto dto) { return dto; }
    public static Dto setter(Dto dto) { dto.name = null; return dto; }
    public static Dto deeperSetter(Dto dto) { return setter(dto); }
    public static void use(Dto dto) { sinkT(dto.email); }

    public static void getterFlow(Dto dto) { sinkAnyT(getter(dto)); }
    public static void calleeSinkFlow(Dto dto) { use(dto); }
    public static void identityFlow(Dto dto) { sinkAnyT(identity(dto)); }
    public static void setterFlow(Dto dto) {
        Dto out = setter(dto);
        sinkT(out.email);
        sinkBlockedT(out.name);
    }
    public static void deeperSetterFlow(Dto dto) {
        Dto out = deeperSetter(dto);
        sinkT(out.email);
        sinkBlockedT(out.name);
    }

    public static Dto wholeSource() { return new Dto(); }
    public static Object exactUSource() { return new Object(); }
    public static Dto factory() {
        Dto dto = wholeSource();
        dto.name = null;
        dto.k = exactUSource();
        return dto;
    }
    public static void factoryFlow() {
        Dto dto = factory();
        sinkT(dto.email);
        sinkU(dto.k);
        sinkBlockedT(dto.name);
    }
    public static void factoryReplayFlow(Object exact) {
        Dto dto = factory();
        sinkT(dto.email);
        sinkU(dto.k);
        sinkBlockedT(dto.name);
        sinkAnyT(mayPass(exact));
    }

    public static native Object mayPass(Object value);
    public static native Object conditionalSource(Object value);
    public static void passFlow(Object exact) { sinkAnyT(mayPass(exact)); }
    public static void sourceFlow(Object exact) { sinkAnyT(conditionalSource(exact)); }

    public static void cleanNamed(Dto dto) { }
    public static void fieldCleanerFlow(Dto dto) {
        cleanNamed(dto);
        sinkT(dto.email);
        sinkBlockedT(dto.name);
    }
    public static void fieldCleanerControl(Dto dto) {
        sinkT(dto.email);
        sinkBlockedT(dto.name);
    }

    public static native Object joint(Object a, Object b);
    public static void jointFlow(Object a, Object b) { sinkJoint(joint(a, b)); }
    public static void jointSplitFlow(Object a, Object b) {
        sinkJoint(joint(a, null));
        sinkJoint(joint(null, b));
    }

    public static Dto entryRemoval(Dto dto) { sinkT(dto.email); return dto; }
    public static void primitiveFlow(byte[] data) { sinkByte(data[0]); }
    public static void sinkT(Object value) { }
    public static void sinkU(Object value) { }
    public static void sinkBlockedT(Object value) { }
    public static void sinkAnyT(Object value) { }
    public static void sinkJoint(Object value) { }
    public static void sinkByte(byte value) { }
}
