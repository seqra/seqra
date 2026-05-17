package analyzerbugs;

import base.RuleSample;
import base.RuleSet;

/**
 * Join-mode mirror of the failing rules/test scenario.
 * Same shape as {@code path-traversal-in-servlet-app} rule: source rule +
 * sink rule joined via {@code untrusted-data.$UNTRUSTED -> sink.$FILE}.
 *
 * In rules/test, Files.write fires but Files.writeString does not. We
 * verify both fire here.
 */
@RuleSet("analyzerbugs/FilesWriteStringJoinBug.yaml")
public abstract class FilesWriteStringJoinBug implements RuleSample {

    public String getParameter(String n) { return "tainted"; }

    public static class PositiveReadAllBytes extends FilesWriteStringJoinBug {
        @Override public void entrypoint() {
            String fileName = getParameter("file");
            java.nio.file.Path path = java.nio.file.Paths.get("/var/data/" + fileName);
            try {
                java.nio.file.Files.readAllBytes(path);
            } catch (java.io.IOException ignored) {}
        }
    }

    public static class PositiveFilesWrite extends FilesWriteStringJoinBug {
        @Override public void entrypoint() {
            String fileName = getParameter("file");
            java.nio.file.Path path = java.nio.file.Paths.get("/var/data/" + fileName);
            try {
                java.nio.file.Files.write(path, "data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException ignored) {}
        }
    }

    public static class PositiveFilesWriteString extends FilesWriteStringJoinBug {
        @Override public void entrypoint() {
            String fileName = getParameter("file");
            java.nio.file.Path path = java.nio.file.Paths.get("/var/data/" + fileName);
            try {
                java.nio.file.Files.writeString(path, "data");
            } catch (java.io.IOException ignored) {}
        }
    }
}
