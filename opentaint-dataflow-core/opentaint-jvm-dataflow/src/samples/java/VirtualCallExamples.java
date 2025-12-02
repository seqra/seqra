public class VirtualCallExamples {

    interface Base {
        String foo(String arg);
    }

    static class Source implements Base {
        public String foo(String arg) {
            return "UNSAFE STRING";
        }
    }

    static class Pass implements Base {
        public String foo(String arg) {
            return arg;
        }
    }

    static class Clean implements Base {
        public String foo(String arg) {
            return "SAFE STRING";
        }
    }

    static void sink(String arg) {
        System.out.println(arg);
    }



    static void simple(Base base) {
        String str = base.foo("something");
        sink(str);
    }

    static void composition(Base base) {
        Base x = base;
        String safe = x.foo("something"); // Safe if base is not Source
        Base y = x;
        String alsoSafe = y.foo(safe); // y === base & x === base
        sink(alsoSafe);
    }

    static void typeCheck(Base base) {
        if (!(base instanceof Clean)) return;

        String str = base.foo("something");
        sink(str);
    }

    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////

    static class BaseWrapper {
        Base base;
    }

    static void fieldAccess(Base base, BaseWrapper wrapper) {
        wrapper.base = base;
        Base x = wrapper.base;

        String str = x.foo("something");
        sink(str);
    }

    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////

    interface BaseFactory {
        Base create();
    }

    class CleanerFactory implements BaseFactory {
        @Override
        public Base create() {
            return new Clean();
        }
    }

    class SourceFactory implements BaseFactory {
        @Override
        public Base create() {
            return new Source();
        }
    }

    static void multipleResolution(BaseFactory factory) {
        Base b = factory.create();
        String str = b.foo("something");
        sink(str);
    }

    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////

    interface BaseValidator {
        Base validate(Base base);
    }

    class DebugValidator implements BaseValidator {
        @Override
        public Base validate(Base base) {
            return base;
        }
    }

    class ProductionValidator implements BaseValidator {
        @Override
        public Base validate(Base base) {
            if (base instanceof Source) return new Clean();
            return base;
        }
    }

    static void nonDistributiveMultipleResolution(Base base, BaseValidator validator) {
        Base b = validator.validate(base);
        String str = b.foo("something");
        sink(str);
    }
}
