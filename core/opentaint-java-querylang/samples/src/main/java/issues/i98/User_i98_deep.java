package issues.i98;

public class User_i98_deep {
    private class Caller {
        void call(Depth2 r, String b) {
            r.depth1.data = b;
        }

        void call(Depth3 r, String b) {
            r.depth2.depth1.data = b;
        }

        void call(Depth4 r, String b) {
            r.depth3.depth2.depth1.data = b;
        }
    }

    private class Depth1 {
        String data = "";
    }

    private class Depth2 {
        Depth1 depth1 = new Depth1();
    }

    private class Depth3 {
        Depth2 depth2 = new Depth2();
    }

    private class Depth4 {
        Depth3 depth3 = new Depth3();
    }

    public String badUser(String badString) {
        Depth2 d2 = new Depth2();
        Depth1 d = d2.depth1;
        Caller k = new Caller();
        k.call(d2, badString);
        return d.data;
    }

    public String badUserDepth4(String badString) {
        Depth4 d4 = new Depth4();
        Depth3 d3 = d4.depth3;
        Caller k = new Caller();
        k.call(d4, badString);
        return d3.depth2.depth1.data;
    }

    public String badUserDepth3(String badString) {
        Depth4 d4 = new Depth4();
        Depth2 d2 = d4.depth3.depth2;
        Caller k = new Caller();
        k.call(d4, badString);
        return d2.depth1.data;
    }

    public String badUserDepth2(String badString) {
        Depth4 d4 = new Depth4();
        Depth1 d1 = d4.depth3.depth2.depth1;
        Caller k = new Caller();
        k.call(d4, badString);
        return d1.data;
    }

    public String badUserDepth4Call2(String badString) {
        Depth4 d4 = new Depth4();
        Depth3 d3 = d4.depth3;
        Caller k = new Caller();
        k.call(d4.depth3.depth2, badString);
        return d3.depth2.depth1.data;
    }

    public String badUserDepth4Call3(String badString) {
        Depth4 d4 = new Depth4();
        Depth2 d2 = d4.depth3.depth2;
        Caller k = new Caller();
        k.call(d4.depth3, badString);
        return d2.depth1.data;
    }
}
