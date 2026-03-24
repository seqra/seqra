package security.dataqueryinjection;

import java.util.HashMap;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;

import org.json.JSONObject;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC samples for pre-existing MongoDB $where injection patterns
 * that were not covered by existing tests.
 */
public class MongoDBInjectionExtraSpringSamples {

    // ── BasicDBObject.put("$where", ...) ─────────────────────────────────────

    @RestController
    @RequestMapping("/mongo/extra")
    public static class UnsafeBasicDBObjectPutController {

        @GetMapping("/unsafe/put")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafePut(@RequestParam("js") String jsExpr) {
            BasicDBObject query = new BasicDBObject();
            query.put("$where", jsExpr);
            return query.toString();
        }
    }

    // ── BasicDBObject.append("$where", ...) ──────────────────────────────────

    @RestController
    @RequestMapping("/mongo/extra/append")
    public static class UnsafeBasicDBObjectAppendController {

        @GetMapping("/unsafe/append")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeAppend(@RequestParam("js") String jsExpr) {
            BasicDBObject query = new BasicDBObject();
            query.append("$where", jsExpr);
            return query.toString();
        }
    }

    // ── Map.put("$where", ...) + BasicDBObject.putAll(Map) ───────────────────

    @RestController
    @RequestMapping("/mongo/extra/putall")
    public static class UnsafePutAllController {

        @GetMapping("/unsafe/putAll")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafePutAll(@RequestParam("js") String jsExpr) {
            Map<String, Object> map = new HashMap<>();
            map.put("$where", jsExpr);
            BasicDBObject query = new BasicDBObject();
            query.putAll(map);
            return query.toString();
        }
    }

    // ── Map.put("$where", ...) + new BasicDBObject(Map) ─────────────────────

    @RestController
    @RequestMapping("/mongo/extra/mapctor")
    public static class UnsafeMapConstructorController {

        @GetMapping("/unsafe/mapConstructor")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeMapConstructor(@RequestParam("js") String jsExpr) {
            Map<String, Object> map = new HashMap<>();
            map.put("$where", jsExpr);
            BasicDBObject query = new BasicDBObject(map);
            return query.toString();
        }
    }

    // ── Map.put("$where", ...) + JSONObject + BasicDBObject.parse(json) ──────

    @RestController
    @RequestMapping("/mongo/extra/jsonparse")
    public static class UnsafeJsonParseController {

        @GetMapping("/unsafe/jsonParse")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeJsonParse(@RequestParam("js") String jsExpr) {
            Map<String, Object> map = new HashMap<>();
            map.put("$where", jsExpr);
            String json = new JSONObject(map).toString();
            BasicDBObject query = new BasicDBObject();
            query.parse(json);
            return query.toString();
        }
    }

    // ── BasicDBObjectBuilder.start().add("$where", ...) ─────────────────────

    @RestController
    @RequestMapping("/mongo/extra/builder/add")
    public static class UnsafeBuilderAddController {

        @GetMapping("/unsafe/builderAdd")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeBuilderAdd(@RequestParam("js") String jsExpr) {
            BasicDBObjectBuilder.start().add("$where", jsExpr);
            return "ok";
        }
    }

    // ── BasicDBObjectBuilder.start().append("$where", ...) ──────────────────

    @RestController
    @RequestMapping("/mongo/extra/builder/append")
    public static class UnsafeBuilderAppendController {

        @GetMapping("/unsafe/builderAppend")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeBuilderAppend(@RequestParam("js") String jsExpr) {
            BasicDBObjectBuilder.start().append("$where", jsExpr);
            return "ok";
        }
    }

    // ── BasicDBObjectBuilder.start("$where", ...) ───────────────────────────

    @RestController
    @RequestMapping("/mongo/extra/builder/start")
    public static class UnsafeBuilderStartController {

        @GetMapping("/unsafe/builderStart")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeBuilderStart(@RequestParam("js") String jsExpr) {
            BasicDBObjectBuilder.start("$where", jsExpr);
            return "ok";
        }
    }

    // ── Map.put("$where", ...) + BasicDBObjectBuilder.start(Map) ────────────

    @RestController
    @RequestMapping("/mongo/extra/builder/startmap")
    public static class UnsafeBuilderStartMapController {

        @GetMapping("/unsafe/builderStartMap")
        @PositiveRuleSample(value = "java/security/data-query-injection.yaml", id = "mongodb-injection-in-spring-app")
        public String unsafeBuilderStartMap(@RequestParam("js") String jsExpr) {
            Map<String, Object> map = new HashMap<>();
            map.put("$where", jsExpr);
            BasicDBObjectBuilder.start(map);
            return "ok";
        }
    }
}
