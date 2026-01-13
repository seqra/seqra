package custom.sampleWithAlias;

import base.EntryPoint;

public class Main {
    @EntryPoint
    public String count(String data) {
        Counter.One counter = new Counter.One(data);
        return counter.oneTwoThree();
    }
}
