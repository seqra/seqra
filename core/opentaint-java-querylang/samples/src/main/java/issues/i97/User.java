package issues.i97;

public class User {
    public static String source() { return "oops"; }

    public static String badMethod() {
        Data d1 = new Data();
        Data d2 = new Data();
        Data d3 = new Data();
        d2.data = source();
        Data[] ds = {d1, d2, d3};
        String o = "";
        for (Data d : ds) {
            o = d.data;
        }
        return o;
    }
}
