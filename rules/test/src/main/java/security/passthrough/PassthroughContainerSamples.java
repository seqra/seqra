package security.passthrough;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regression samples for element movement through containers.
 *
 * Arrays are carried by the {@code [*]} element accessor and collections by the
 * {@code Element}/{@code MapKey}/{@code MapValue} slots, and both are only accepted where
 * the destination can actually hold an element (JIRFactTypeChecker rejects an element
 * accessor on anything that is neither an array nor java.lang.Object). Every unsafe method
 * moves one tainted element through one container operation into Runtime.exec; the safe
 * twin runs the same operation over constants.
 */
@RestController
@RequestMapping("/passthrough/containers")
public class PassthroughContainerSamples {

    private static final String CONSTANT = "release-notes.txt";

    // === arrays ===

    @GetMapping("/array-element/unsafe")
    public void arrayElementUnsafe(@RequestParam String input) throws IOException {
        String[] files = new String[] { input };
        Runtime.getRuntime().exec("cat " + files[0]);
    }

    @GetMapping("/array-element/safe")
    public void arrayElementSafe(@RequestParam String input) throws IOException {
        String[] files = new String[] { CONSTANT };
        Runtime.getRuntime().exec("cat " + files[0]);
    }

    @GetMapping("/arrays-copy-of/unsafe")
    public void arraysCopyOfUnsafe(@RequestParam String input) throws IOException {
        String[] files = new String[] { input };
        String[] copy = Arrays.copyOf(files, 1);
        Runtime.getRuntime().exec("cat " + copy[0]);
    }

    @GetMapping("/arrays-copy-of/safe")
    public void arraysCopyOfSafe(@RequestParam String input) throws IOException {
        String[] files = new String[] { CONSTANT };
        String[] copy = Arrays.copyOf(files, 1);
        Runtime.getRuntime().exec("cat " + copy[0]);
    }

    @GetMapping("/array-copy/unsafe")
    public void systemArrayCopyUnsafe(@RequestParam String input) throws IOException {
        String[] source = new String[] { input };
        String[] target = new String[1];
        System.arraycopy(source, 0, target, 0, 1);
        Runtime.getRuntime().exec("cat " + target[0]);
    }

    @GetMapping("/array-copy/safe")
    public void systemArrayCopySafe(@RequestParam String input) throws IOException {
        String[] source = new String[] { CONSTANT };
        String[] target = new String[1];
        System.arraycopy(source, 0, target, 0, 1);
        Runtime.getRuntime().exec("cat " + target[0]);
    }

    @GetMapping("/arrays-as-list/unsafe")
    public void arraysAsListUnsafe(@RequestParam String input) throws IOException {
        List<String> files = Arrays.asList(new String[] { input });
        Runtime.getRuntime().exec("cat " + files.get(0));
    }

    @GetMapping("/arrays-as-list/safe")
    public void arraysAsListSafe(@RequestParam String input) throws IOException {
        List<String> files = Arrays.asList(new String[] { CONSTANT });
        Runtime.getRuntime().exec("cat " + files.get(0));
    }

    @GetMapping("/list-of-varargs/unsafe")
    public void listOfVarargsUnsafe(@RequestParam String input) throws IOException {
        List<String> files = List.of(new String[] { input });
        Runtime.getRuntime().exec("cat " + files.get(0));
    }

    @GetMapping("/list-of-varargs/safe")
    public void listOfVarargsSafe(@RequestParam String input) throws IOException {
        List<String> files = List.of(new String[] { CONSTANT });
        Runtime.getRuntime().exec("cat " + files.get(0));
    }

    @GetMapping("/set-of-varargs/unsafe")
    public void setOfVarargsUnsafe(@RequestParam String input) throws IOException {
        Set<String> files = Set.of(new String[] { input });
        Runtime.getRuntime().exec("cat " + files.iterator().next());
    }

    @GetMapping("/set-of-varargs/safe")
    public void setOfVarargsSafe(@RequestParam String input) throws IOException {
        Set<String> files = Set.of(new String[] { CONSTANT });
        Runtime.getRuntime().exec("cat " + files.iterator().next());
    }

    @GetMapping("/stream-of-varargs/unsafe")
    public void streamOfVarargsUnsafe(@RequestParam String input) throws IOException {
        Stream<String> files = Stream.of(new String[] { input });
        Runtime.getRuntime().exec("cat " + files.findFirst().orElse(CONSTANT));
    }

    @GetMapping("/stream-of-varargs/safe")
    public void streamOfVarargsSafe(@RequestParam String input) throws IOException {
        Stream<String> files = Stream.of(new String[] { CONSTANT });
        Runtime.getRuntime().exec("cat " + files.findFirst().orElse(CONSTANT));
    }

    // === lists ===

    @GetMapping("/list-get/unsafe")
    public void listGetUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        Runtime.getRuntime().exec("cat " + files.get(0));
    }

    @GetMapping("/list-get/safe")
    public void listGetSafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        Runtime.getRuntime().exec("cat " + files.get(0));
    }

    @GetMapping("/list-iterate/unsafe")
    public void listIterateUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        for (String file : files) {
            Runtime.getRuntime().exec("cat " + file);
        }
    }

    @GetMapping("/list-iterate/safe")
    public void listIterateSafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        for (String file : files) {
            Runtime.getRuntime().exec("cat " + file);
        }
    }

    @GetMapping("/list-copy/unsafe")
    public void listCopyUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        List<String> copy = new ArrayList<>(files);
        Runtime.getRuntime().exec("cat " + copy.get(0));
    }

    @GetMapping("/list-copy/safe")
    public void listCopySafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        List<String> copy = new ArrayList<>(files);
        Runtime.getRuntime().exec("cat " + copy.get(0));
    }

    @GetMapping("/list-unmodifiable/unsafe")
    public void listUnmodifiableUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        List<String> view = Collections.unmodifiableList(files);
        Runtime.getRuntime().exec("cat " + view.get(0));
    }

    @GetMapping("/list-unmodifiable/safe")
    public void listUnmodifiableSafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        List<String> view = Collections.unmodifiableList(files);
        Runtime.getRuntime().exec("cat " + view.get(0));
    }

    @GetMapping("/list-to-array/unsafe")
    public void listToArrayUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        String[] array = files.toArray(new String[0]);
        Runtime.getRuntime().exec("cat " + array[0]);
    }

    @GetMapping("/list-to-array/safe")
    public void listToArraySafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        String[] array = files.toArray(new String[0]);
        Runtime.getRuntime().exec("cat " + array[0]);
    }

    @GetMapping("/list-join/unsafe")
    public void listJoinUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        Runtime.getRuntime().exec("cat " + String.join(" ", files));
    }

    @GetMapping("/list-join/safe")
    public void listJoinSafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        Runtime.getRuntime().exec("cat " + String.join(" ", files));
    }

    @GetMapping("/list-stream/unsafe")
    public void listStreamUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        List<String> collected = files.stream().collect(Collectors.toList());
        Runtime.getRuntime().exec("cat " + collected.get(0));
    }

    @GetMapping("/list-stream/safe")
    public void listStreamSafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        List<String> collected = files.stream().collect(Collectors.toList());
        Runtime.getRuntime().exec("cat " + collected.get(0));
    }

    @GetMapping("/linked-list/unsafe")
    public void linkedListUnsafe(@RequestParam String input) throws IOException {
        LinkedList<String> files = new LinkedList<>();
        files.addFirst(input);
        Runtime.getRuntime().exec("cat " + files.getFirst());
    }

    @GetMapping("/linked-list/safe")
    public void linkedListSafe(@RequestParam String input) throws IOException {
        LinkedList<String> files = new LinkedList<>();
        files.addFirst(CONSTANT);
        Runtime.getRuntime().exec("cat " + files.getFirst());
    }

    // === sets ===

    @GetMapping("/set-iterate/unsafe")
    public void setIterateUnsafe(@RequestParam String input) throws IOException {
        Set<String> files = new HashSet<>();
        files.add(input);
        Runtime.getRuntime().exec("cat " + files.iterator().next());
    }

    @GetMapping("/set-iterate/safe")
    public void setIterateSafe(@RequestParam String input) throws IOException {
        Set<String> files = new HashSet<>();
        files.add(CONSTANT);
        Runtime.getRuntime().exec("cat " + files.iterator().next());
    }

    // === maps ===

    @GetMapping("/map-value/unsafe")
    public void mapValueUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", input);
        Runtime.getRuntime().exec("cat " + files.get("name"));
    }

    @GetMapping("/map-value/safe")
    public void mapValueSafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", CONSTANT);
        Runtime.getRuntime().exec("cat " + files.get("name"));
    }

    @GetMapping("/map-key/unsafe")
    public void mapKeyUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put(input, "value");
        Runtime.getRuntime().exec("cat " + files.keySet().iterator().next());
    }

    @GetMapping("/map-key/safe")
    public void mapKeySafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put(CONSTANT, "value");
        Runtime.getRuntime().exec("cat " + files.keySet().iterator().next());
    }

    @GetMapping("/map-values/unsafe")
    public void mapValuesUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", input);
        Runtime.getRuntime().exec("cat " + files.values().iterator().next());
    }

    @GetMapping("/map-values/safe")
    public void mapValuesSafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", CONSTANT);
        Runtime.getRuntime().exec("cat " + files.values().iterator().next());
    }

    @GetMapping("/map-entry/unsafe")
    public void mapEntryUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", input);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Runtime.getRuntime().exec("cat " + entry.getValue());
        }
    }

    @GetMapping("/map-entry/safe")
    public void mapEntrySafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", CONSTANT);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Runtime.getRuntime().exec("cat " + entry.getValue());
        }
    }

    @GetMapping("/properties/unsafe")
    public void propertiesUnsafe(@RequestParam String input) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("name", input);
        Runtime.getRuntime().exec("cat " + properties.getProperty("name"));
    }

    @GetMapping("/properties/safe")
    public void propertiesSafe(@RequestParam String input) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("name", CONSTANT);
        Runtime.getRuntime().exec("cat " + properties.getProperty("name"));
    }

    @GetMapping("/map-get-or-default/unsafe")
    public void mapGetOrDefaultUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", input);
        Runtime.getRuntime().exec("cat " + files.getOrDefault("name", CONSTANT));
    }

    @GetMapping("/map-get-or-default/safe")
    public void mapGetOrDefaultSafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", CONSTANT);
        Runtime.getRuntime().exec("cat " + files.getOrDefault("name", CONSTANT));
    }

    /** A nested container: the element of the inner list has to survive both hops. */
    @GetMapping("/nested-map/unsafe")
    public void nestedMapUnsafe(@RequestParam String input) throws IOException {
        Map<String, List<String>> files = new HashMap<>();
        List<String> names = new ArrayList<>();
        names.add(input);
        files.put("names", names);
        Runtime.getRuntime().exec("cat " + files.get("names").get(0));
    }

    @GetMapping("/nested-map/safe")
    public void nestedMapSafe(@RequestParam String input) throws IOException {
        Map<String, List<String>> files = new HashMap<>();
        List<String> names = new ArrayList<>();
        names.add(CONSTANT);
        files.put("names", names);
        Runtime.getRuntime().exec("cat " + files.get("names").get(0));
    }

    // === bulk moves between containers ===

    @GetMapping("/list-add-all/unsafe")
    public void listAddAllUnsafe(@RequestParam String input) throws IOException {
        List<String> source = new ArrayList<>();
        source.add(input);
        List<String> target = new ArrayList<>();
        target.addAll(source);
        Runtime.getRuntime().exec("cat " + target.get(0));
    }

    @GetMapping("/list-add-all/safe")
    public void listAddAllSafe(@RequestParam String input) throws IOException {
        List<String> source = new ArrayList<>();
        source.add(CONSTANT);
        List<String> target = new ArrayList<>();
        target.addAll(source);
        Runtime.getRuntime().exec("cat " + target.get(0));
    }

    @GetMapping("/collections-add-all/unsafe")
    public void collectionsAddAllUnsafe(@RequestParam String input) throws IOException {
        List<String> target = new ArrayList<>();
        Collections.addAll(target, input);
        Runtime.getRuntime().exec("cat " + target.get(0));
    }

    @GetMapping("/collections-add-all/safe")
    public void collectionsAddAllSafe(@RequestParam String input) throws IOException {
        List<String> target = new ArrayList<>();
        Collections.addAll(target, CONSTANT);
        Runtime.getRuntime().exec("cat " + target.get(0));
    }

    @GetMapping("/map-put-all/unsafe")
    public void mapPutAllUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> source = new HashMap<>();
        source.put("name", input);
        Map<String, String> target = new HashMap<>();
        target.putAll(source);
        Runtime.getRuntime().exec("cat " + target.get("name"));
    }

    @GetMapping("/map-put-all/safe")
    public void mapPutAllSafe(@RequestParam String input) throws IOException {
        Map<String, String> source = new HashMap<>();
        source.put("name", CONSTANT);
        Map<String, String> target = new HashMap<>();
        target.putAll(source);
        Runtime.getRuntime().exec("cat " + target.get("name"));
    }

    /**
     * Not registered in rule-test.yaml: Collectors#joining collapses the stream elements
     * into one String, and a collector-aware model would have to know which collector was
     * passed to collect(..). Modelling it at the Stream#collect level would taint the root
     * of every collected container, so the gap is left open rather than over-approximated.
     */
    @GetMapping("/stream-joining/unsafe")
    public void streamJoiningUnsafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(input);
        Runtime.getRuntime().exec("cat " + files.stream().collect(Collectors.joining(" ")));
    }

    @GetMapping("/stream-joining/safe")
    public void streamJoiningSafe(@RequestParam String input) throws IOException {
        List<String> files = new ArrayList<>();
        files.add(CONSTANT);
        Runtime.getRuntime().exec("cat " + files.stream().collect(Collectors.joining(" ")));
    }

    @GetMapping("/deque-poll/unsafe")
    public void dequePollUnsafe(@RequestParam String input) throws IOException {
        Deque<String> files = new ArrayDeque<>();
        files.push(input);
        Runtime.getRuntime().exec("cat " + files.poll());
    }

    @GetMapping("/deque-poll/safe")
    public void dequePollSafe(@RequestParam String input) throws IOException {
        Deque<String> files = new ArrayDeque<>();
        files.push(CONSTANT);
        Runtime.getRuntime().exec("cat " + files.poll());
    }
}
