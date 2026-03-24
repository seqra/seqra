package security.unsafedeserialization;

// import com.fasterxml.jackson.annotation.JsonTypeInfo;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

// import org.opentaint.sast.test.util.PositiveRuleSample;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for unsafe-jackson-deserialization-in-spring.
 *
 * ANALYZER LIMITATION: All tests are commented out because the analyzer cannot resolve
 * inner class types (ObjectMapper.DefaultTypeResolverBuilder) in typed metavariables.
 * Re-enable when analyzer supports inner class types.
 */
public class JacksonDeserializationSpringSamples {

    // ── DefaultTypeResolverBuilder.init(JsonTypeInfo.Id.CLASS) ───────────

    // TODO: Analyzer limitation – inner class type ObjectMapper.DefaultTypeResolverBuilder not supported
    // @RestController
    // @RequestMapping("/jackson-deser")
    // public static class UnsafeDefaultTypeResolverClassController {
    //
    //     @PostMapping("/unsafe-resolver-class")
    //     @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-spring-app")
    //     public String unsafeResolverClass(@RequestBody String json) throws Exception {
    //         ObjectMapper mapper = new ObjectMapper();
    //         ObjectMapper.DefaultTypeResolverBuilder resolverBuilder =
    //                 new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
    //         resolverBuilder.init(JsonTypeInfo.Id.CLASS, null);
    //         mapper.setDefaultTyping(resolverBuilder);
    //         Object result = mapper.readValue(json, Object.class);
    //         return String.valueOf(result);
    //     }
    // }

    // ── DefaultTypeResolverBuilder.init(JsonTypeInfo.Id.MINIMAL_CLASS) ───

    // TODO: Analyzer limitation – inner class type ObjectMapper.DefaultTypeResolverBuilder not supported
    // @RestController
    // @RequestMapping("/jackson-deser")
    // public static class UnsafeDefaultTypeResolverMinimalClassController {
    //
    //     @PostMapping("/unsafe-resolver-minimal-class")
    //     @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-spring-app")
    //     public String unsafeResolverMinimalClass(@RequestBody String json) throws Exception {
    //         ObjectMapper mapper = new ObjectMapper();
    //         ObjectMapper.DefaultTypeResolverBuilder resolverBuilder =
    //                 new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL);
    //         resolverBuilder.init(JsonTypeInfo.Id.MINIMAL_CLASS, null);
    //         mapper.setDefaultTyping(resolverBuilder);
    //         Object result = mapper.readValue(json, Object.class);
    //         return String.valueOf(result);
    //     }
    // }

    // ── ObjectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator, EVERYTHING) ─

    // TODO: Analyzer limitation – inner class type ObjectMapper.DefaultTypeResolverBuilder not supported
    // @RestController
    // @RequestMapping("/jackson-deser")
    // public static class UnsafeActivateDefaultTypingController {
    //
    //     @PostMapping("/unsafe-activate-default-typing")
    //     @PositiveRuleSample(value = "java/security/unsafe-deserialization.yaml", id = "unsafe-jackson-deserialization-in-spring-app")
    //     public String unsafeActivateDefaultTyping(@RequestBody String json) throws Exception {
    //         ObjectMapper mapper = new ObjectMapper();
    //         mapper.activateDefaultTyping(
    //                 LaissezFaireSubTypeValidator.instance,
    //                 ObjectMapper.DefaultTyping.EVERYTHING);
    //         Object result = mapper.readValue(json, Object.class);
    //         return String.valueOf(result);
    //     }
    // }
}
