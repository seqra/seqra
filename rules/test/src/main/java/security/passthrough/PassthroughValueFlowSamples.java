package security.passthrough;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.FileSystems;
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.BasicControl;
import javax.naming.ldap.Rdn;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortKey;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import javax.xml.namespace.QName;

import javax.faces.model.ArrayDataModel;
import javax.faces.model.SelectItem;
import javax.faces.model.SelectItemGroup;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.util.ObjectBuffer;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regression samples for the value-carrying passThrough models in {@code model/java/config}.
 *
 * Each unsafe/safe pair pins one library model: the unsafe method must keep the taint
 * flowing from the request parameter through the modelled call chain into
 * {@code Runtime.exec}, the safe twin runs the identical chain over a constant and must
 * not report. The pairs exist so that a change to a passthrough slot (its name, its
 * declared value type, or its owner) is caught here instead of silently turning into a
 * false negative in the field.
 */
@RestController
@RequestMapping("/passthrough/value-flow")
public class PassthroughValueFlowSamples {

    private static final String CONSTANT = "release-notes.txt";

    // === java.lang.AbstractStringBuilder#content ===

    /** StringBuilder#append(String) -> StringBuilder#toString. */
    @GetMapping("/string-builder/unsafe")
    public void stringBuilderAppendUnsafe(@RequestParam String input) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("cat ");
        builder.append(input);
        Runtime.getRuntime().exec(builder.toString());
    }

    @GetMapping("/string-builder/safe")
    public void stringBuilderAppendSafe(@RequestParam String input) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("cat ");
        builder.append(CONSTANT);
        Runtime.getRuntime().exec(builder.toString());
    }

    /** StringBuilder#append(char[]) - the element-star carrier of the char array. */
    @GetMapping("/string-builder-chars/unsafe")
    public void stringBuilderAppendCharsUnsafe(@RequestParam String input) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(input.toCharArray());
        Runtime.getRuntime().exec(builder.toString());
    }

    @GetMapping("/string-builder-chars/safe")
    public void stringBuilderAppendCharsSafe(@RequestParam String input) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(CONSTANT.toCharArray());
        Runtime.getRuntime().exec(builder.toString());
    }

    /** StringBuffer#insert(int, String). */
    @GetMapping("/string-buffer/unsafe")
    public void stringBufferInsertUnsafe(@RequestParam String input) throws IOException {
        StringBuffer buffer = new StringBuffer("cat ");
        buffer.insert(4, input);
        Runtime.getRuntime().exec(buffer.toString());
    }

    @GetMapping("/string-buffer/safe")
    public void stringBufferInsertSafe(@RequestParam String input) throws IOException {
        StringBuffer buffer = new StringBuffer("cat ");
        buffer.insert(4, CONSTANT);
        Runtime.getRuntime().exec(buffer.toString());
    }

    // === java.lang.String#format - the argument is boxed into an Object[] element ===

    @GetMapping("/string-format/unsafe")
    public void stringFormatUnsafe(@RequestParam String input) throws IOException {
        String command = String.format("cat %s", input);
        Runtime.getRuntime().exec(command);
    }

    @GetMapping("/string-format/safe")
    public void stringFormatSafe(@RequestParam String input) throws IOException {
        String command = String.format("cat %s", CONSTANT);
        Runtime.getRuntime().exec(command);
    }

    // === java.util.StringJoiner ===

    /** The joined element must reach StringJoiner#toString. */
    @GetMapping("/string-joiner-add/unsafe")
    public void stringJoinerAddUnsafe(@RequestParam String input) throws IOException {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("cat");
        joiner.add(input);
        Runtime.getRuntime().exec(joiner.toString());
    }

    @GetMapping("/string-joiner-add/safe")
    public void stringJoinerAddSafe(@RequestParam String input) throws IOException {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("cat");
        joiner.add(CONSTANT);
        Runtime.getRuntime().exec(joiner.toString());
    }

    /** The delimiter itself is a carrier too - it ends up in the joined output. */
    @GetMapping("/string-joiner-delimiter/unsafe")
    public void stringJoinerDelimiterUnsafe(@RequestParam String input) throws IOException {
        StringJoiner joiner = new StringJoiner(input);
        joiner.add("cat");
        joiner.add("file");
        Runtime.getRuntime().exec(joiner.toString());
    }

    @GetMapping("/string-joiner-delimiter/safe")
    public void stringJoinerDelimiterSafe(@RequestParam String input) throws IOException {
        StringJoiner joiner = new StringJoiner(CONSTANT);
        joiner.add("cat");
        joiner.add("file");
        Runtime.getRuntime().exec(joiner.toString());
    }

    // === java.util.regex.Matcher#input ===

    /** Pattern#matcher stores the input, Matcher#group reads it back. */
    @GetMapping("/matcher-group/unsafe")
    public void matcherGroupUnsafe(@RequestParam String input) throws IOException {
        Matcher matcher = Pattern.compile("(.*)").matcher(input);
        if (matcher.find()) {
            Runtime.getRuntime().exec("cat " + matcher.group());
        }
    }

    @GetMapping("/matcher-group/safe")
    public void matcherGroupSafe(@RequestParam String input) throws IOException {
        Matcher matcher = Pattern.compile("(.*)").matcher(CONSTANT);
        if (matcher.find()) {
            Runtime.getRuntime().exec("cat " + matcher.group());
        }
    }

    /** Matcher#appendTail writes the remaining input into the target builder. */
    @GetMapping("/matcher-append-tail/unsafe")
    public void matcherAppendTailUnsafe(@RequestParam String input) throws IOException {
        Matcher matcher = Pattern.compile("^cat").matcher(input);
        StringBuffer buffer = new StringBuffer();
        matcher.appendTail(buffer);
        Runtime.getRuntime().exec(buffer.toString());
    }

    @GetMapping("/matcher-append-tail/safe")
    public void matcherAppendTailSafe(@RequestParam String input) throws IOException {
        Matcher matcher = Pattern.compile("^cat").matcher(CONSTANT);
        StringBuffer buffer = new StringBuffer();
        matcher.appendTail(buffer);
        Runtime.getRuntime().exec(buffer.toString());
    }

    // === java.text.MessageFormat#pattern ===

    @GetMapping("/message-format/unsafe")
    public void messageFormatUnsafe(@RequestParam String input) throws IOException {
        MessageFormat format = new MessageFormat(input);
        String command = format.format(new Object[] { "arg" });
        Runtime.getRuntime().exec(command);
    }

    @GetMapping("/message-format/safe")
    public void messageFormatSafe(@RequestParam String input) throws IOException {
        MessageFormat format = new MessageFormat(CONSTANT);
        String command = format.format(new Object[] { "arg" });
        Runtime.getRuntime().exec(command);
    }

    // === java.io.ByteArrayOutputStream#buffer ===

    @GetMapping("/byte-array-output-stream/unsafe")
    public void byteArrayOutputStreamUnsafe(@RequestParam String input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(input.getBytes());
        Runtime.getRuntime().exec(new String(out.toByteArray()));
    }

    @GetMapping("/byte-array-output-stream/safe")
    public void byteArrayOutputStreamSafe(@RequestParam String input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(CONSTANT.getBytes());
        Runtime.getRuntime().exec(new String(out.toByteArray()));
    }

    // === java.io.Reader#content ===

    @GetMapping("/string-reader/unsafe")
    public void stringReaderUnsafe(@RequestParam String input) throws IOException {
        StringReader reader = new StringReader(input);
        char[] chars = new char[64];
        reader.read(chars);
        Runtime.getRuntime().exec(new String(chars));
    }

    @GetMapping("/string-reader/safe")
    public void stringReaderSafe(@RequestParam String input) throws IOException {
        StringReader reader = new StringReader(CONSTANT);
        char[] chars = new char[64];
        reader.read(chars);
        Runtime.getRuntime().exec(new String(chars));
    }

    // === java.net.URI#path -> java.io.File#path ===

    /** File(URI) must preserve taint from the URI path component. */
    @GetMapping("/uri-file-path/unsafe")
    public void uriFilePathUnsafe(@RequestParam String input)
            throws IOException, URISyntaxException {
        URI uri = new URI("file", null, input, null);
        File file = new File(uri);
        Runtime.getRuntime().exec(file.toString());
    }

    /** A URI fragment is not part of the filesystem path consumed by File(URI). */
    @GetMapping("/uri-file-fragment/safe")
    public void uriFragmentDoesNotReachFilePathSafe(@RequestParam String input)
            throws IOException, URISyntaxException {
        URI uri = new URI("file", null, CONSTANT, input);
        File file = new File(uri);
        Runtime.getRuntime().exec(file.toString());
    }

    // === java.nio buffers ===

    @GetMapping("/byte-buffer/unsafe")
    public void byteBufferUnsafe(@RequestParam String input) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        ByteBuffer returned = buffer.put(input.getBytes());
        Runtime.getRuntime().exec(new String(returned.array()));
    }

    @GetMapping("/byte-buffer/safe")
    public void byteBufferSafe(@RequestParam String input) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        ByteBuffer returned = buffer.put(CONSTANT.getBytes());
        Runtime.getRuntime().exec(new String(returned.array()));
    }

    /** Buffer metadata text must not inherit taint stored only in the content slot. */
    @GetMapping("/byte-buffer-metadata/safe")
    public void byteBufferContentDoesNotReachMetadataSafe(@RequestParam String input)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        buffer.put(input.getBytes());
        Runtime.getRuntime().exec(buffer.toString());
    }

    /** A typed ByteBuffer view must retain the backing buffer's element taint. */
    @GetMapping("/byte-buffer-char-view/unsafe")
    public void byteBufferCharViewUnsafe(@RequestParam String input) throws IOException {
        CharBuffer view = ByteBuffer.wrap(input.getBytes()).asCharBuffer();
        Runtime.getRuntime().exec(view.toString());
    }

    @GetMapping("/byte-buffer-char-view/safe")
    public void byteBufferCharViewSafe(@RequestParam String input) throws IOException {
        CharBuffer view = ByteBuffer.wrap(CONSTANT.getBytes()).asCharBuffer();
        Runtime.getRuntime().exec(view.toString());
    }

    /** The shared carrier is content-only; it must not taint view metadata. */
    @GetMapping("/byte-buffer-view-metadata/safe")
    public void byteBufferViewContentDoesNotReachMetadataSafe(@RequestParam String input)
            throws IOException {
        Runtime.getRuntime().exec(ByteBuffer.wrap(input.getBytes()).asIntBuffer().toString());
    }

    @GetMapping("/char-buffer/unsafe")
    public void charBufferUnsafe(@RequestParam String input) throws IOException {
        CharBuffer buffer = CharBuffer.allocate(256);
        buffer.put(input);
        Runtime.getRuntime().exec(buffer.flip().toString());
    }

    @GetMapping("/char-buffer/safe")
    public void charBufferSafe(@RequestParam String input) throws IOException {
        CharBuffer buffer = CharBuffer.allocate(256);
        buffer.put(CONSTANT);
        Runtime.getRuntime().exec(buffer.flip().toString());
    }

    /** The Locale overload boxes its arguments into the same Object[] as the two-arg one. */
    @GetMapping("/string-format-locale/unsafe")
    public void stringFormatLocaleUnsafe(@RequestParam String input) throws IOException {
        String command = String.format(Locale.US, "cat %s", input);
        Runtime.getRuntime().exec(command);
    }

    @GetMapping("/string-format-locale/safe")
    public void stringFormatLocaleSafe(@RequestParam String input) throws IOException {
        String command = String.format(Locale.US, "cat %s", CONSTANT);
        Runtime.getRuntime().exec(command);
    }

    // === precision: flows the type system says cannot happen ===

    /**
     * length() returns an int and a mark on a primitive is rejected outright
     * (JIRFactTypeChecker.kt:87), so no amount of modelling may make this report.
     */
    @GetMapping("/primitive-result/safe")
    public void primitiveResultSafe(@RequestParam String input) throws IOException {
        int length = input.length();
        Runtime.getRuntime().exec("cat report-" + length);
    }

    /** A map value must not reach the key side of the same map. */
    @GetMapping("/map-value-not-key/safe")
    public void mapValueDoesNotReachKeySafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put("name", input);
        Runtime.getRuntime().exec("cat " + files.keySet().iterator().next());
    }

    /** ...and a map key must not reach the value side. */
    @GetMapping("/map-key-not-value/safe")
    public void mapKeyDoesNotReachValueSafe(@RequestParam String input) throws IOException {
        Map<String, String> files = new HashMap<>();
        files.put(input, "constant");
        Runtime.getRuntime().exec("cat " + files.values().iterator().next());
    }

    // === models that fill a caller-supplied buffer (arg, not result) ===

    /** String#getChars(int,int,char[],int) copies into the destination array. */
    @GetMapping("/string-get-chars/unsafe")
    public void stringGetCharsUnsafe(@RequestParam String input) throws IOException {
        char[] chars = new char[input.length()];
        input.getChars(0, input.length(), chars, 0);
        Runtime.getRuntime().exec(new String(chars));
    }

    @GetMapping("/string-get-chars/safe")
    public void stringGetCharsSafe(@RequestParam String input) throws IOException {
        char[] chars = new char[CONSTANT.length()];
        CONSTANT.getChars(0, CONSTANT.length(), chars, 0);
        Runtime.getRuntime().exec(new String(chars));
    }

    /** ByteBuffer#get(byte[]) drains the buffer into the destination array. */
    @GetMapping("/byte-buffer-get/unsafe")
    public void byteBufferGetUnsafe(@RequestParam String input) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(input.getBytes());
        byte[] drained = new byte[input.length()];
        buffer.get(drained);
        Runtime.getRuntime().exec(new String(drained));
    }

    @GetMapping("/byte-buffer-get/safe")
    public void byteBufferGetSafe(@RequestParam String input) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(CONSTANT.getBytes());
        byte[] drained = new byte[CONSTANT.length()];
        buffer.get(drained);
        Runtime.getRuntime().exec(new String(drained));
    }

    /** CharBuffer#get(char[]) does the same for chars. */
    @GetMapping("/char-buffer-get/unsafe")
    public void charBufferGetUnsafe(@RequestParam String input) throws IOException {
        CharBuffer buffer = CharBuffer.wrap(input.toCharArray());
        char[] drained = new char[input.length()];
        buffer.get(drained);
        Runtime.getRuntime().exec(new String(drained));
    }

    @GetMapping("/char-buffer-get/safe")
    public void charBufferGetSafe(@RequestParam String input) throws IOException {
        CharBuffer buffer = CharBuffer.wrap(CONSTANT.toCharArray());
        char[] drained = new char[CONSTANT.length()];
        buffer.get(drained);
        Runtime.getRuntime().exec(new String(drained));
    }

    // === javax.naming slots ===

    /** BasicControl stores the control OID, getID reads it back. */
    @GetMapping("/ldap-control-id/unsafe")
    public void basicControlIdUnsafe(@RequestParam String input) throws IOException {
        BasicControl control = new BasicControl(input);
        Runtime.getRuntime().exec("ldapsearch " + control.getID());
    }

    @GetMapping("/ldap-control-id/safe")
    public void basicControlIdSafe(@RequestParam String input) throws IOException {
        BasicControl control = new BasicControl(CONSTANT);
        Runtime.getRuntime().exec("ldapsearch " + control.getID());
    }

    /** Rdn keeps the attribute type in its own slot. */
    @GetMapping("/ldap-rdn-type/unsafe")
    public void rdnTypeUnsafe(@RequestParam String input) throws IOException, NamingException {
        Rdn rdn = new Rdn(input, "value");
        Runtime.getRuntime().exec("ldapsearch " + rdn.getType());
    }

    @GetMapping("/ldap-rdn-type/safe")
    public void rdnTypeSafe(@RequestParam String input) throws IOException, NamingException {
        Rdn rdn = new Rdn(CONSTANT, "value");
        Runtime.getRuntime().exec("ldapsearch " + rdn.getType());
    }

    /** SortKey keeps the matching rule id apart from the attribute id. */
    @GetMapping("/ldap-sort-key/unsafe")
    public void sortKeyMatchingRuleUnsafe(@RequestParam String input) throws IOException {
        SortKey key = new SortKey("cn", true, input);
        Runtime.getRuntime().exec("ldapsearch " + key.getMatchingRuleID());
    }

    @GetMapping("/ldap-sort-key/safe")
    public void sortKeyMatchingRuleSafe(@RequestParam String input) throws IOException {
        SortKey key = new SortKey("cn", true, CONSTANT);
        Runtime.getRuntime().exec("ldapsearch " + key.getMatchingRuleID());
    }

    /** Reference keeps the class name in its own slot. */
    @GetMapping("/naming-reference/unsafe")
    public void referenceClassNameUnsafe(@RequestParam String input) throws IOException {
        Reference reference = new Reference(input);
        Runtime.getRuntime().exec("jndi " + reference.getClassName());
    }

    @GetMapping("/naming-reference/safe")
    public void referenceClassNameSafe(@RequestParam String input) throws IOException {
        Reference reference = new Reference(CONSTANT);
        Runtime.getRuntime().exec("jndi " + reference.getClassName());
    }

    // === javax.xml.namespace.QName field isolation ===

    @GetMapping("/qname-local-part/unsafe")
    public void qNameLocalPartUnsafe(@RequestParam String input) throws IOException {
        QName name = new QName("urn:constant", input, "constant");
        Runtime.getRuntime().exec("cat " + name.getLocalPart());
    }

    @GetMapping("/qname-prefix/unsafe")
    public void qNamePrefixUnsafe(@RequestParam String input) throws IOException {
        QName name = new QName("urn:constant", "constant", input);
        Runtime.getRuntime().exec("cat " + name.getPrefix());
    }

    /** Taint in the namespace URI must not leak into the independent local-part field. */
    @GetMapping("/qname-namespace-not-local/safe")
    public void qNameNamespaceDoesNotReachLocalPartSafe(@RequestParam String input)
            throws IOException {
        QName name = new QName(input, CONSTANT, "constant");
        Runtime.getRuntime().exec("cat " + name.getLocalPart());
    }

    /** Taint in the prefix must not leak into the independent namespace field. */
    @GetMapping("/qname-prefix-not-namespace/safe")
    public void qNamePrefixDoesNotReachNamespaceSafe(@RequestParam String input)
            throws IOException {
        QName name = new QName("urn:constant", CONSTANT, input);
        Runtime.getRuntime().exec("cat " + name.getNamespaceURI());
    }

    // === org.springframework.http.HttpHeaders ===

    @GetMapping("/http-headers/unsafe")
    public void httpHeadersUnsafe(@RequestParam String input) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Command", input);
        Runtime.getRuntime().exec("cat " + headers.getFirst("X-Command"));
    }

    @GetMapping("/http-headers/safe")
    public void httpHeadersSafe(@RequestParam String input) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Command", CONSTANT);
        Runtime.getRuntime().exec("cat " + headers.getFirst("X-Command"));
    }

    // === regressions for precise array-element and void-return modelling ===

    @GetMapping("/message-format-static-pattern/unsafe")
    public void messageFormatStaticPatternUnsafe(@RequestParam String input) throws IOException {
        Runtime.getRuntime().exec(MessageFormat.format(input, CONSTANT));
    }

    @GetMapping("/message-format-static-pattern/safe")
    public void messageFormatStaticPatternSafe(@RequestParam String input) throws IOException {
        Runtime.getRuntime().exec(MessageFormat.format(CONSTANT, CONSTANT));
    }

    @GetMapping("/message-format-static-argument/unsafe")
    public void messageFormatStaticArgumentUnsafe(@RequestParam String input) throws IOException {
        Runtime.getRuntime().exec(MessageFormat.format("cat {0}", input));
    }

    @GetMapping("/message-format-static-argument/safe")
    public void messageFormatStaticArgumentSafe(@RequestParam String input) throws IOException {
        Runtime.getRuntime().exec(MessageFormat.format("cat {0}", CONSTANT));
    }

    /** Formatting must not taint or overwrite the independent argument-array slot. */
    @GetMapping("/message-format-no-argument-backflow/safe")
    public void messageFormatDoesNotFlowBackToArgumentsSafe(@RequestParam String input)
            throws IOException {
        Object[] arguments = new Object[] { CONSTANT };
        MessageFormat.format(input, arguments);
        Runtime.getRuntime().exec((String) arguments[0]);
    }

    @GetMapping("/choice-format-formats/unsafe")
    public void choiceFormatFormatsUnsafe(@RequestParam String input) throws IOException {
        ChoiceFormat format = new ChoiceFormat(new double[] { 0 }, new String[] { input });
        Runtime.getRuntime().exec(format.format(0));
    }

    @GetMapping("/choice-format-formats/safe")
    public void choiceFormatFormatsSafe(@RequestParam String input) throws IOException {
        ChoiceFormat format = new ChoiceFormat(new double[] { 0 }, new String[] { CONSTANT });
        Runtime.getRuntime().exec(format.format(0));
    }

    /** A tainted format element must not contaminate the independent limits array. */
    @GetMapping("/choice-format-no-formats-to-limits/safe")
    public void choiceFormatFormatsDoNotReachLimitsSafe(@RequestParam String input)
            throws IOException {
        ChoiceFormat format = new ChoiceFormat(new double[] { 0 }, new String[] { input });
        Runtime.getRuntime().exec("cat " + format.getLimits()[0]);
    }

    @GetMapping("/object-buffer-list/unsafe")
    public void objectBufferListUnsafe(@RequestParam String input) throws IOException {
        ObjectBuffer buffer = new ObjectBuffer();
        List<Object> output = new ArrayList<>();
        buffer.completeAndClearBuffer(new Object[] { input }, 1, output);
        Runtime.getRuntime().exec((String) output.get(0));
    }

    @GetMapping("/object-buffer-list/safe")
    public void objectBufferListSafe(@RequestParam String input) throws IOException {
        ObjectBuffer buffer = new ObjectBuffer();
        List<Object> output = new ArrayList<>();
        buffer.completeAndClearBuffer(new Object[] { CONSTANT }, 1, output);
        Runtime.getRuntime().exec((String) output.get(0));
    }

    @GetMapping("/json-generator-binary/unsafe")
    public void jsonGeneratorBinaryUnsafe(@RequestParam String input) throws IOException {
        byte[] bytes = input.getBytes();
        TokenBuffer generator = new TokenBuffer(null, false);
        generator.writeBinary(Base64Variants.getDefaultVariant(), bytes, 0, bytes.length);
        try (JsonParser parser = generator.asParser()) {
            parser.nextToken();
            Runtime.getRuntime().exec(new String(parser.getBinaryValue()));
        }
    }

    @GetMapping("/json-generator-binary/safe")
    public void jsonGeneratorBinarySafe(@RequestParam String input) throws IOException {
        byte[] bytes = CONSTANT.getBytes();
        TokenBuffer generator = new TokenBuffer(null, false);
        generator.writeBinary(Base64Variants.getDefaultVariant(), bytes, 0, bytes.length);
        try (JsonParser parser = generator.asParser()) {
            parser.nextToken();
            Runtime.getRuntime().exec(new String(parser.getBinaryValue()));
        }
    }

    @GetMapping("/json-array-builder-indexed/unsafe")
    public void jsonArrayBuilderIndexedUnsafe(@RequestParam String input) throws IOException {
        JsonArrayBuilder builder = Json.createArrayBuilder().add(CONSTANT);
        JsonArrayBuilder returned = builder.add(0, input);
        Runtime.getRuntime().exec(returned.build().getString(0));
    }

    /** The position argument controls placement but is not JSON array content. */
    @GetMapping("/json-array-builder-index/safe")
    public void jsonArrayBuilderIndexSafe(@RequestParam String input) throws IOException {
        JsonArrayBuilder builder = Json.createArrayBuilder().add(CONSTANT);
        JsonArrayBuilder returned = builder.add(Integer.parseInt(input), CONSTANT);
        Runtime.getRuntime().exec(returned.build().getString(0));
    }

    @GetMapping("/json-object-builder-nested/unsafe")
    public void jsonObjectBuilderNestedUnsafe(@RequestParam String input) throws IOException {
        JsonObjectBuilder nested = Json.createObjectBuilder().add("command", input);
        JsonObjectBuilder outer = Json.createObjectBuilder().add("nested", nested);
        Runtime.getRuntime().exec(outer.build().getJsonObject("nested").getString("command"));
    }

    /** A removal key is control input and must not become surviving object content. */
    @GetMapping("/json-object-builder-remove/safe")
    public void jsonObjectBuilderRemoveKeySafe(@RequestParam String input) throws IOException {
        JsonObjectBuilder builder = Json.createObjectBuilder().add("command", CONSTANT);
        builder.remove(input);
        Runtime.getRuntime().exec(builder.build().getString("command"));
    }

    @GetMapping("/list-replace-all/unsafe")
    public void listReplaceAllUnsafe(@RequestParam String input) throws IOException {
        List<String> values = new ArrayList<>();
        values.add(input);
        values.replaceAll(String::trim);
        Runtime.getRuntime().exec(values.get(0));
    }

    @GetMapping("/list-replace-all/safe")
    public void listReplaceAllSafe(@RequestParam String input) throws IOException {
        List<String> values = new ArrayList<>();
        values.add(CONSTANT);
        values.replaceAll(String::trim);
        Runtime.getRuntime().exec(values.get(0));
    }

    @GetMapping("/concurrent-map-replace-all/unsafe")
    public void concurrentMapReplaceAllUnsafe(@RequestParam String input) throws IOException {
        Map<String, String> values = new ConcurrentHashMap<>();
        values.put("command", input);
        values.replaceAll((key, value) -> value.trim());
        Runtime.getRuntime().exec(values.get("command"));
    }

    @GetMapping("/concurrent-map-replace-all/safe")
    public void concurrentMapReplaceAllSafe(@RequestParam String input) throws IOException {
        Map<String, String> values = new ConcurrentHashMap<>();
        values.put("command", CONSTANT);
        values.replaceAll((key, value) -> value.trim());
        Runtime.getRuntime().exec(values.get("command"));
    }

    /** Replacing values must not merge the independent key and value slots. */
    @GetMapping("/concurrent-map-replace-all-no-key-to-value/safe")
    public void concurrentMapReplaceAllDoesNotMixKeysSafe(@RequestParam String input)
            throws IOException {
        Map<String, String> values = new ConcurrentHashMap<>();
        values.put(input, CONSTANT);
        values.replaceAll((key, value) -> value.trim());
        Runtime.getRuntime().exec(values.values().iterator().next());
    }

    @GetMapping("/file-system-path-varargs/unsafe")
    public void fileSystemPathVarargsUnsafe(@RequestParam String input) throws IOException {
        Runtime.getRuntime().exec(FileSystems.getDefault().getPath("tmp", input).toString());
    }

    @GetMapping("/file-system-path-varargs/safe")
    public void fileSystemPathVarargsSafe(@RequestParam String input) throws IOException {
        Runtime.getRuntime().exec(FileSystems.getDefault().getPath("tmp", CONSTANT).toString());
    }

    @GetMapping("/search-controls-attributes/unsafe")
    public void searchControlsAttributesUnsafe(@RequestParam String input) throws IOException {
        SearchControls controls = new SearchControls();
        controls.setReturningAttributes(new String[] { input });
        Runtime.getRuntime().exec(controls.getReturningAttributes()[0]);
    }

    @GetMapping("/search-controls-attributes/safe")
    public void searchControlsAttributesSafe(@RequestParam String input) throws IOException {
        SearchControls controls = new SearchControls();
        controls.setReturningAttributes(new String[] { CONSTANT });
        Runtime.getRuntime().exec(controls.getReturningAttributes()[0]);
    }

    @GetMapping("/basic-control-payload/unsafe")
    public void basicControlPayloadUnsafe(@RequestParam String input) throws IOException {
        BasicControl control = new BasicControl("1.2.3", false, input.getBytes());
        Runtime.getRuntime().exec(new String(control.getEncodedValue()));
    }

    @GetMapping("/basic-control-payload/safe")
    public void basicControlPayloadSafe(@RequestParam String input) throws IOException {
        BasicControl control = new BasicControl("1.2.3", false, CONSTANT.getBytes());
        Runtime.getRuntime().exec(new String(control.getEncodedValue()));
    }

    /** The OID and encoded payload are separate fields of the same control. */
    @GetMapping("/basic-control-no-id-to-payload/safe")
    public void basicControlIdDoesNotReachPayloadSafe(@RequestParam String input)
            throws IOException {
        BasicControl control = new BasicControl(input, false, CONSTANT.getBytes());
        Runtime.getRuntime().exec(new String(control.getEncodedValue()));
    }

    @GetMapping("/sort-control-array/unsafe")
    public void sortControlArrayUnsafe(@RequestParam String input) throws IOException {
        SortControl control = new SortControl(new String[] { input }, false);
        Runtime.getRuntime().exec(new String(control.getEncodedValue()));
    }

    @GetMapping("/sort-control-array/safe")
    public void sortControlArraySafe(@RequestParam String input) throws IOException {
        SortControl control = new SortControl(new String[] { CONSTANT }, false);
        Runtime.getRuntime().exec(new String(control.getEncodedValue()));
    }

    @GetMapping("/cached-row-set-table-name/unsafe")
    public void cachedRowSetTableNameUnsafe(@RequestParam String input) throws Exception {
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setTableName(input);
        Runtime.getRuntime().exec(rows.getTableName());
    }

    @GetMapping("/cached-row-set-table-name/safe")
    public void cachedRowSetTableNameSafe(@RequestParam String input) throws Exception {
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setTableName(CONSTANT);
        Runtime.getRuntime().exec(rows.getTableName());
    }

    @GetMapping("/faces-array-data-model/unsafe")
    public void facesArrayDataModelUnsafe(@RequestParam String input) throws IOException {
        ArrayDataModel<String> model = new ArrayDataModel<>(new String[] { input });
        model.setRowIndex(0);
        Runtime.getRuntime().exec(model.getRowData());
    }

    @GetMapping("/faces-array-data-model/safe")
    public void facesArrayDataModelSafe(@RequestParam String input) throws IOException {
        ArrayDataModel<String> model = new ArrayDataModel<>(new String[] { CONSTANT });
        model.setRowIndex(0);
        Runtime.getRuntime().exec(model.getRowData());
    }

    @GetMapping("/faces-select-item-group/unsafe")
    public void facesSelectItemGroupUnsafe(@RequestParam String input) throws IOException {
        SelectItemGroup group = new SelectItemGroup();
        group.setSelectItems(new SelectItem[] { new SelectItem(input) });
        Runtime.getRuntime().exec((String) group.getSelectItems()[0].getValue());
    }

    @GetMapping("/faces-select-item-group/safe")
    public void facesSelectItemGroupSafe(@RequestParam String input) throws IOException {
        SelectItemGroup group = new SelectItemGroup();
        group.setSelectItems(new SelectItem[] { new SelectItem(CONSTANT) });
        Runtime.getRuntime().exec((String) group.getSelectItems()[0].getValue());
    }
}
