package example.util;

public class CustomType1 {
    public CustomType2 mkType2() {
        return new CustomType2();
    }

    public static CustomType1 mkType1FromType3(CustomType3 t3) {
        return new CustomType1();
    }
}
