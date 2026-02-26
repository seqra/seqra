package test.samples;

public class StaticMethodSample {
    private static String mkStr() {
        return "static";
    }

    public static String staticField = mkStr();

    public static String staticMethod() {
        return /*staticCall:start*/staticField.toLowerCase()/*staticCall:end*/;
    }

    public static /*staticMethodEntry:start*/void staticMethodWithArgs(String arg)/*staticMethodEntry:end*/ {
        System.out.println(arg);
    /*staticMethodExit:start*/}/*staticMethodExit:end*/
}
