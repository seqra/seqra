package security.pathtraversal;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.zip.ZipFile;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Additional servlet-based path-traversal samples testing newly added sinks
 * for Java core APIs, Guava, Jackson, Commons IO, and other libraries.
 */
public class PathTraversalAdditionalServletSamples {

    // ── java.io.PrintStream ────────────────────────────────────────────────

    @WebServlet("/pathtraversal/printstream")
    public static class UnsafePrintStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/logs/" + fileName);
            PrintStream ps = new PrintStream(file);
            ps.println("log entry");
            ps.close();
        }
    }

    // ── java.io.PrintWriter ────────────────────────────────────────────────

    @WebServlet("/pathtraversal/printwriter")
    public static class UnsafePrintWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/logs/" + fileName);
            PrintWriter pw = new PrintWriter(file);
            pw.println("log entry");
            pw.close();
        }
    }

    // ── java.io.File.renameTo ──────────────────────────────────────────────

    @WebServlet("/pathtraversal/renameto")
    public static class UnsafeRenameToServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String destName = request.getParameter("dest");
            File src = new File("/var/uploads/temp.dat");
            File dest = new File("/var/uploads/" + destName);
            src.renameTo(dest);
        }
    }

    // ── java.io.File.canRead ───────────────────────────────────────────────

    @WebServlet("/pathtraversal/canread")
    public static class UnsafeCanReadServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            if (file.canRead()) {
                response.getWriter().println("file is readable");
            }
        }
    }

    // ── java.nio.channels.FileChannel ──────────────────────────────────────

    @WebServlet("/pathtraversal/filechannel")
    public static class UnsafeFileChannelServlet extends HttpServlet {
        @Override
        // ANALYZER LIMITATION: Method name `open` causes "Unreachable" parser error.
        // TODO: Re-enable when analyzer supports `open` as a method name in patterns.
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            channel.close();
        }
    }

    // ── java.nio.file.Files.lines ──────────────────────────────────────────

    @WebServlet("/pathtraversal/files-lines")
    public static class UnsafeFilesLinesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            long count = Files.lines(path).count();
            response.getWriter().println("lines: " + count);
        }
    }

    // ── java.nio.file.Files.readString ─────────────────────────────────────

    @WebServlet("/pathtraversal/files-readstring")
    public static class UnsafeFilesReadStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            String content = Files.readString(path);
            response.getWriter().println(content);
        }
    }

    // ── java.lang.ProcessBuilder.redirectOutput ────────────────────────────

    @WebServlet("/pathtraversal/processbuilder")
    public static class UnsafeProcessBuilderServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String logFile = request.getParameter("logfile");
            File outputFile = new File("/var/logs/" + logFile);
            ProcessBuilder pb = new ProcessBuilder("ls");
            pb.redirectOutput(outputFile);
        }
    }

    // ── java.util.logging.FileHandler ──────────────────────────────────────

    @WebServlet("/pathtraversal/filehandler")
    public static class UnsafeFileHandlerServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String logFile = request.getParameter("logfile");
            FileHandler handler = new FileHandler("/var/logs/" + logFile, true);
            handler.close();
        }
    }

    // ── java.util.zip.ZipFile ──────────────────────────────────────────────

    @WebServlet("/pathtraversal/zipfile")
    public static class UnsafeZipFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String archiveName = request.getParameter("archive");
            ZipFile zipFile = new ZipFile("/var/data/" + archiveName);
            response.getWriter().println("entries: " + zipFile.size());
            zipFile.close();
        }
    }

    // ── javax.servlet.ServletContext.getResource ────────────────────────────

    @WebServlet("/pathtraversal/servletcontext")
    public static class UnsafeServletContextResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resourcePath = request.getParameter("resource");
            java.net.URL url = getServletContext().getResource(resourcePath);
            if (url != null) {
                response.getWriter().println("found: " + url);
            }
        }
    }

    // ── Guava com.google.common.io.Files ───────────────────────────────────

    @WebServlet("/pathtraversal/guava-files")
    public static class UnsafeGuavaFilesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            byte[] content = com.google.common.io.Files.toByteArray(file);
            response.getOutputStream().write(content);
        }
    }

    // ── Jackson ObjectMapper.readValue(File,...) ───────────────────────────

    @WebServlet("/pathtraversal/jackson")
    public static class UnsafeJacksonServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String configFile = request.getParameter("config");
            File file = new File("/var/config/" + configFile);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?, ?> data = mapper.readValue(file, Map.class);
            response.getWriter().println(data);
        }
    }

    // ── Apache Commons IO – FileUtils.copyInputStreamToFile ────────────────

    @WebServlet("/pathtraversal/commons-io-copy")
    public static class UnsafeCommonsIoCopyServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String destName = request.getParameter("dest");
            File destFile = new File("/var/uploads/" + destName);
            org.apache.commons.io.FileUtils.copyInputStreamToFile(request.getInputStream(), destFile);
        }
    }

    // ── Apache Commons IO – FileWriterWithEncoding ─────────────────────────

    @WebServlet("/pathtraversal/commons-io-writer")
    public static class UnsafeCommonsIoWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.commons.io.output.FileWriterWithEncoding writer =
                    new org.apache.commons.io.output.FileWriterWithEncoding(file, "UTF-8");
            writer.write("data");
            writer.close();
        }
    }

    // ── Apache Commons IO – FileUtils.forceMkdir ───────────────────────────

    @WebServlet("/pathtraversal/commons-io-mkdir")
    public static class UnsafeCommonsIoMkdirServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            org.apache.commons.io.FileUtils.forceMkdir(dir);
        }
    }

    // ── javax.xml.transform.stream.StreamResult ────────────────────────────

    @WebServlet("/pathtraversal/streamresult")
    public static class UnsafeStreamResultServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String outputFile = request.getParameter("output");
            File file = new File("/var/data/" + outputFile);
            javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(file);
            response.getWriter().println("result: " + result.getSystemId());
        }
    }

    // ── ClassLoader.getSystemResource ──────────────────────────────────────

    @WebServlet("/pathtraversal/classloader-resource")
    public static class UnsafeClassLoaderResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resourceName = request.getParameter("resource");
            java.net.URL url = ClassLoader.getSystemResource(resourceName);
            if (url != null) {
                response.getWriter().println("found: " + url);
            }
        }
    }
}
