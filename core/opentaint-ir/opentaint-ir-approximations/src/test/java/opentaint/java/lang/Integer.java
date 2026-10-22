package opentaint.java.lang;

public class Integer {
    private final int value;

    public Integer(int value) {
        this.value = value;
    }

    public static Integer valueOf(int value) {
        return new Integer(value);
    }

    public int getValue() {
        return value;
    }
}
