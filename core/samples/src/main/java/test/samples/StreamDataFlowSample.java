package test.samples;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StreamDataFlowSample {

    public void streamMapCollectFlow() {
        String data = source();
        List<String> list = new ArrayList<>();
        list.add(data);
        List<String> result = list.stream()
                .map(s -> s.trim())
                .collect(Collectors.toList());
        sink(result.get(0));
    }

    public void streamFilterCollectFlow() {
        String data = source();
        List<String> list = new ArrayList<>();
        list.add(data);
        List<String> result = list.stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        sink(result.get(0));
    }

    public void streamReduceFlow() {
        String data = source();
        List<String> list = new ArrayList<>();
        list.add(data);
        String result = list.stream()
                .reduce("", String::concat);
        sink(result);
    }

    public void streamForEachFlow() {
        String data = source();
        List<String> list = new ArrayList<>();
        list.add(data);
        list.stream().forEach(s -> sink(s));
    }

    public void streamFlatMapFlow() {
        String data = source();
        List<List<String>> outer = new ArrayList<>();
        List<String> inner = new ArrayList<>();
        inner.add(data);
        outer.add(inner);
        List<String> result = outer.stream()
                .flatMap(l -> l.stream())
                .collect(Collectors.toList());
        sink(result.get(0));
    }

    public String source() { return "tainted"; }
    public void sink(String data) { }
}
