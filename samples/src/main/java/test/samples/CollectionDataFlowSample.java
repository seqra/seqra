package test.samples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CollectionDataFlowSample {

    public void listAddGetFlow() {
        String data = source();
        List<String> list = new ArrayList<>();
        list.add(data);
        String retrieved = list.get(0);
        sink(retrieved);
    }

    public void mapPutGetFlow() {
        String data = source();
        Map<String, String> map = new HashMap<>();
        map.put("key", data);
        String retrieved = map.get("key");
        sink(retrieved);
    }

    public void iteratorFlow() {
        String data = source();
        List<String> list = new ArrayList<>();
        list.add(data);
        Iterator<String> it = list.iterator();
        String retrieved = it.next();
        sink(retrieved);
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}
