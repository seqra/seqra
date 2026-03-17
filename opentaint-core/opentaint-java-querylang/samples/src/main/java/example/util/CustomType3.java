package example.util;

public class CustomType3 {
    public CustomType3() {
    }

    public CustomType3(CustomType1 t1) {
    }

    public CustomType1 mkType1() {
        return new CustomType1();
    }

    public void sanitize() {
        // no-op sanitizer
    }
}
