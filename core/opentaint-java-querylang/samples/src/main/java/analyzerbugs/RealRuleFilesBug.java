package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Reproduces the rules/test FN for
 * PathTraversalNioSinksSamples$UnsafeWriteStringServlet using the
 * production servlet source pattern and a tightened sink rule with both
 * Files.readAllBytes and Files.writeString.
 *
 * The Positive variants mirror the two NIO sinks in rules/test:
 *   PositiveReadAllBytes  -> Files.readAllBytes (works in rules/test)
 *   PositiveWriteString   -> Files.writeString (FN in rules/test)
 * Both should be reported because the same tainted path reaches both sinks.
 */
@RuleSet("analyzerbugs/RealRuleFilesBug.yaml")
public abstract class RealRuleFilesBug implements RuleSample {

    // A stand-in for HttpServletRequest.getParameter that the rule's
    // entry-point pattern will treat as untrusted via a metavariable regex.
    public String getParameter(String n) { return "tainted"; }

    public static class PositiveReadAllBytes extends RealRuleFilesBug {
        @Override public void entrypoint() {
            String fileName = getParameter("file");
            java.nio.file.Path path = java.nio.file.Paths.get("/var/data/" + fileName);
            try {
                java.nio.file.Files.readAllBytes(path);
            } catch (java.io.IOException ignored) {}
        }
    }

    public static class PositiveWriteString extends RealRuleFilesBug {
        @Override public void entrypoint() {
            String fileName = getParameter("file");
            java.nio.file.Path path = java.nio.file.Paths.get("/var/data/" + fileName);
            try {
                java.nio.file.Files.writeString(path, "data");
            } catch (java.io.IOException ignored) {}
        }
    }
}
