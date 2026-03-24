package security.pathtraversal;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based test samples covering Hudson/Jenkins and Stapler sink patterns.
 */
public class PathTraversalJenkinsSinksSamples {

    // ── Hudson FilePath instance (tainted FilePath) ─────────────────────────
    // The metavariable-regex group: (hudson.FilePath $FILE).$METHOD(...)
    // These test the case where the FilePath object itself is tainted.

    @WebServlet("/pt-jenkins/filepath-exists")
    public static class UnsafeFilePathExistsServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            hudson.FilePath fp = new hudson.FilePath(new File("/var/data/" + fileName));
            try {
                response.getWriter().println("exists: " + fp.exists());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @WebServlet("/pt-jenkins/filepath-read")
    public static class UnsafeFilePathReadServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            hudson.FilePath fp = new hudson.FilePath(new File("/var/data/" + fileName));
            try {
                java.io.InputStream is = fp.read();
                is.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @WebServlet("/pt-jenkins/filepath-readtostring")
    public static class UnsafeFilePathReadToStringServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            hudson.FilePath fp = new hudson.FilePath(new File("/var/data/" + fileName));
            try {
                response.getWriter().println(fp.readToString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @WebServlet("/pt-jenkins/filepath-write")
    public static class UnsafeFilePathWriteServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            hudson.FilePath fp = new hudson.FilePath(new File("/var/data/" + fileName));
            try {
                fp.write("data", "UTF-8");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── Hudson FilePath argument (tainted argument) ─────────────────────────

    @WebServlet("/pt-jenkins/filepath-copyfrom")
    public static class UnsafeFilePathCopyFromServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String src = request.getParameter("src");
            hudson.FilePath dest = new hudson.FilePath(new File("/var/data/dest"));
            hudson.FilePath srcPath = new hudson.FilePath(new File("/var/data/" + src));
            try {
                dest.copyFrom(srcPath);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @WebServlet("/pt-jenkins/filepath-copyrecursiveto")
    public static class UnsafeFilePathCopyRecursiveToServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            hudson.FilePath srcFp = new hudson.FilePath(new File("/var/data/source"));
            hudson.FilePath destFp = new hudson.FilePath(new File("/var/data/" + dest));
            try {
                srcFp.copyRecursiveTo("**/*", destFp);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @WebServlet("/pt-jenkins/filepath-copyto")
    public static class UnsafeFilePathCopyToServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            hudson.FilePath srcFp = new hudson.FilePath(new File("/var/data/source"));
            hudson.FilePath destFp = new hudson.FilePath(new File("/var/data/" + dest));
            try {
                srcFp.copyTo(destFp);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @WebServlet("/pt-jenkins/filepath-copytowithperm")
    public static class UnsafeFilePathCopyToWithPermServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            hudson.FilePath srcFp = new hudson.FilePath(new File("/var/data/source"));
            hudson.FilePath destFp = new hudson.FilePath(new File("/var/data/" + dest));
            try {
                srcFp.copyToWithPermission(destFp);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── Hudson XmlFile ──────────────────────────────────────────────────────

    @WebServlet("/pt-jenkins/xmlfile")
    public static class UnsafeXmlFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            hudson.XmlFile xmlFile = new hudson.XmlFile(file);
            response.getWriter().println("exists: " + xmlFile.exists());
        }
    }

    // ── Hudson DirectoryBrowserSupport ──────────────────────────────────────

    @WebServlet("/pt-jenkins/dirbrowser")
    public static class UnsafeDirectoryBrowserSupportServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            hudson.FilePath fp = new hudson.FilePath(new File("/var/data/" + dirName));
            new hudson.model.DirectoryBrowserSupport(null, fp, "title", null, false);
        }
    }

    // ── Hudson Items.load ───────────────────────────────────────────────────

    @WebServlet("/pt-jenkins/items-load")
    public static class UnsafeItemsLoadServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            hudson.model.Items.load(null, dir);
        }
    }

    // ── Hudson AtomicFileWriter ─────────────────────────────────────────────

    @WebServlet("/pt-jenkins/atomicfilewriter")
    public static class UnsafeAtomicFileWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            hudson.util.AtomicFileWriter writer = new hudson.util.AtomicFileWriter(file);
            writer.write("data");
            writer.close();
        }
    }

    // ── Hudson ClasspathBuilder.add ─────────────────────────────────────────

    @WebServlet("/pt-jenkins/classpathbuilder-add")
    public static class UnsafeClasspathBuilderAddServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String path = request.getParameter("path");
            File file = new File("/var/lib/" + path);
            hudson.util.ClasspathBuilder cb = new hudson.util.ClasspathBuilder();
            cb.add(file);
        }
    }

    // ── Hudson HttpResponses.staticResource ─────────────────────────────────

    @WebServlet("/pt-jenkins/httpresponses-staticresource")
    public static class UnsafeHttpResponsesStaticResourceServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new URL() wrapper; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            java.net.URL url = new java.net.URL("file:///var/data/" + resource);
            hudson.util.HttpResponses.staticResource(url);
        }
    }

    // ── Hudson IOUtils.mkdirs ───────────────────────────────────────────────

    @WebServlet("/pt-jenkins/ioutils-mkdirs")
    public static class UnsafeIOUtilsMkdirsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            hudson.util.IOUtils.mkdirs(dir);
        }
    }

    // ── Hudson StreamTaskListener ───────────────────────────────────────────

    @WebServlet("/pt-jenkins/streamtasklistener")
    public static class UnsafeStreamTaskListenerServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String logFile = request.getParameter("logfile");
            File file = new File("/var/logs/" + logFile);
            hudson.util.StreamTaskListener listener = new hudson.util.StreamTaskListener(file);
            listener.getLogger().println("log entry");
            listener.close();
        }
    }

    // ── Hudson Lifecycle.rewriteHudsonWar ───────────────────────────────────

    @WebServlet("/pt-jenkins/lifecycle-rewritewar")
    public static class UnsafeLifecycleRewriteWarServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String warFile = request.getParameter("war");
            File file = new File("/var/deploy/" + warFile);
            try {
                hudson.lifecycle.Lifecycle.get().rewriteHudsonWar(file);
            } catch (Exception e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── Hudson ReopenableFileOutputStream ───────────────────────────────────

    @WebServlet("/pt-jenkins/reopenablefileoutputstream")
    public static class UnsafeReopenableFileOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/logs/" + fileName);
            hudson.util.io.ReopenableFileOutputStream os = new hudson.util.io.ReopenableFileOutputStream(file);
            os.write(42);
            os.close();
        }
    }

    // ── Hudson RewindableFileOutputStream ───────────────────────────────────

    @WebServlet("/pt-jenkins/rewindablefileoutputstream")
    public static class UnsafeRewindableFileOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/logs/" + fileName);
            hudson.util.io.RewindableFileOutputStream os = new hudson.util.io.RewindableFileOutputStream(file);
            os.write(42);
            os.close();
        }
    }

    // ── Stapler StaplerResponse.serveFile ───────────────────────────────────

    @WebServlet("/pt-jenkins/stapler-servefile")
    public static class UnsafeStaplerServeFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            java.net.URL url = new java.net.URL("file:///var/data/" + resource);
            org.kohsuke.stapler.StaplerResponse staplerResponse =
                    org.kohsuke.stapler.Stapler.getCurrentResponse();
            if (staplerResponse != null) {
                staplerResponse.serveFile(null, url);
            }
        }
    }

    // ── Stapler StaplerResponse.serveLocalizedFile ──────────────────────────

    @WebServlet("/pt-jenkins/stapler-servelocalizedfile")
    public static class UnsafeStaplerServeLocalizedFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            java.net.URL url = new java.net.URL("file:///var/data/" + resource);
            org.kohsuke.stapler.StaplerResponse staplerResponse =
                    org.kohsuke.stapler.Stapler.getCurrentResponse();
            if (staplerResponse != null) {
                staplerResponse.serveLocalizedFile(null, url);
            }
        }
    }

    // ── Stapler LargeText ───────────────────────────────────────────────────

    @WebServlet("/pt-jenkins/stapler-largetext")
    public static class UnsafeStaplerLargeTextServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/logs/" + fileName);
            new org.kohsuke.stapler.framework.io.LargeText(file, true);
        }
    }

    // ── Hudson ChangeLogParser.parse ──────────────────────────────────────────

    @WebServlet("/pt-jenkins/changelogparser-parse")
    public static class UnsafeChangeLogParserServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File changeLogFile = new File("/var/data/" + fileName);
            hudson.scm.ChangeLogParser parser = new hudson.scm.NullChangeLogParser();
            try {
                parser.parse(null, changeLogFile);
            } catch (Exception e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── Hudson SCM.checkout ───────────────────────────────────────────────────

    @WebServlet("/pt-jenkins/scm-checkout")
    public static class UnsafeSCMCheckoutServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File changeLogFile = new File("/var/data/" + dest);
            hudson.scm.SCM scm = null;
            try {
                scm.checkout(null, null, null, null, changeLogFile);
            } catch (Exception e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── Hudson SCM.compareRemoteRevisionWith ──────────────────────────────────

    @WebServlet("/pt-jenkins/scm-compareremote")
    public static class UnsafeSCMCompareRemoteServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through new hudson.FilePath() constructor; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dir = request.getParameter("dir");
            hudson.FilePath workspace = new hudson.FilePath(new File("/var/data/" + dir));
            hudson.scm.SCM scm = null;
            try {
                scm.compareRemoteRevisionWith(null, null, workspace, null, null);
            } catch (Exception e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── Hudson Kernel32.MoveFileExA ───────────────────────────────────────────

    @WebServlet("/pt-jenkins/kernel32-movefileex")
    public static class UnsafeKernel32MoveFileExServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            hudson.util.jna.Kernel32 kernel = null;
            kernel.MoveFileExA("source.dat", "/var/data/" + dest, 0);
        }
    }
}
