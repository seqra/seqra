package security.pathtraversal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based test samples covering third-party library sink patterns:
 * Guava Files, Jackson ObjectMapper, XStream, Netty, Undertow, zip4j, ANTLR,
 * Apache Ant, Kotlin FilesKt, and JMH.
 */
public class PathTraversalLibSinksSamples {

    // ── Guava Files.asByteSink ──────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-asbytesink")
    public static class UnsafeGuavaAsByteSinkServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            com.google.common.io.Files.asByteSink(file).write("data".getBytes());
        }
    }

    // ── Guava Files.asCharSink ──────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-ascharsink")
    public static class UnsafeGuavaAsCharSinkServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            com.google.common.io.Files.asCharSink(file, StandardCharsets.UTF_8).write("data");
        }
    }

    // ── Guava Files.asCharSource ────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-ascharsource")
    public static class UnsafeGuavaAsCharSourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            String content = com.google.common.io.Files.asCharSource(file, StandardCharsets.UTF_8).read();
            response.getWriter().println(content);
        }
    }

    // ── Guava Files.newWriter ───────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-newwriter")
    public static class UnsafeGuavaNewWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            java.io.BufferedWriter writer = com.google.common.io.Files.newWriter(file, StandardCharsets.UTF_8);
            writer.write("data");
            writer.close();
        }
    }

    // ── Guava Files.readLines ───────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-readlines")
    public static class UnsafeGuavaReadLinesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("lines: " + com.google.common.io.Files.readLines(file, StandardCharsets.UTF_8).size());
        }
    }

    // ── Guava Files.toString ────────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-tostring")
    public static class UnsafeGuavaToStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        @SuppressWarnings("deprecation")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println(com.google.common.io.Files.toString(file, StandardCharsets.UTF_8));
        }
    }

    // ── Guava Files.write ───────────────────────────────────────────────────

    @WebServlet("/pt-lib/guava-write")
    public static class UnsafeGuavaWriteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        @SuppressWarnings("deprecation")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            com.google.common.io.Files.write("data", file, StandardCharsets.UTF_8);
        }
    }

    // ── Jackson ObjectMapper.writeValue(File,...) ───────────────────────────

    @WebServlet("/pt-lib/jackson-writevalue")
    public static class UnsafeJacksonWriteValueServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.writeValue(file, java.util.Collections.singletonMap("key", "value"));
        }
    }

    // ── XStream.fromXML(File) ───────────────────────────────────────────────

    @WebServlet("/pt-lib/xstream-fromxml")
    public static class UnsafeXStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            com.thoughtworks.xstream.XStream xstream = new com.thoughtworks.xstream.XStream();
            Object obj = xstream.fromXML(file);
            response.getWriter().println("result: " + obj);
        }
    }

    // ── Netty HttpPostRequestEncoder.addBodyFileUpload ──────────────────────

    @WebServlet("/pt-lib/netty-addfileupload")
    public static class UnsafeNettyFileUploadServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            try {
                io.netty.handler.codec.http.DefaultFullHttpRequest nettyReq = new io.netty.handler.codec.http.DefaultFullHttpRequest(
                        io.netty.handler.codec.http.HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.POST, "/upload");
                io.netty.handler.codec.http.multipart.HttpPostRequestEncoder encoder =
                        new io.netty.handler.codec.http.multipart.HttpPostRequestEncoder(nettyReq, true);
                encoder.addBodyFileUpload("file", file, "application/octet-stream", false);
            } catch (Exception e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── Netty SslContextBuilder.forServer ───────────────────────────────────

    @WebServlet("/pt-lib/netty-sslforserver")
    public static class UnsafeNettySslForServerServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String certFile = request.getParameter("cert");
            File file = new File("/var/certs/" + certFile);
            io.netty.handler.ssl.SslContextBuilder.forServer(file, new File("/var/certs/key.pem"));
        }
    }

    // ── Netty SslContextBuilder.trustManager ────────────────────────────────

    @WebServlet("/pt-lib/netty-ssltrustmanager")
    public static class UnsafeNettySslTrustManagerServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String certFile = request.getParameter("cert");
            File file = new File("/var/certs/" + certFile);
            io.netty.handler.ssl.SslContextBuilder.forClient().trustManager(file);
        }
    }

    // ── Netty PlatformDependent.createTempFile ──────────────────────────────

    @WebServlet("/pt-lib/netty-createtempfile")
    public static class UnsafeNettyCreateTempFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/tmp/" + dirName);
            File temp = io.netty.util.internal.PlatformDependent.createTempFile("prefix", ".tmp", dir);
            response.getWriter().println(temp.getAbsolutePath());
        }
    }

    // ── Undertow PathResourceManager.getResource ────────────────────────────

    @WebServlet("/pt-lib/undertow-getresource")
    public static class UnsafeUndertowGetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resourcePath = request.getParameter("path");
            io.undertow.server.handlers.resource.PathResourceManager manager =
                    new io.undertow.server.handlers.resource.PathResourceManager(Paths.get("/var/www"));
            io.undertow.server.handlers.resource.Resource resource = manager.getResource(resourcePath);
            if (resource != null) {
                response.getWriter().println("found: " + resource.getPath());
            }
        }
    }

    // ── zip4j ZipFile.extractAll ────────────────────────────────────────────

    @WebServlet("/pt-lib/zip4j-extractall")
    public static class UnsafeZip4jExtractAllServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String destDir = request.getParameter("dest");
            try {
                net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile("/var/data/archive.zip");
                zipFile.extractAll("/var/data/" + destDir);
            } catch (net.lingala.zip4j.exception.ZipException e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── ANTLR ANTLRFileStream ───────────────────────────────────────────────

    @WebServlet("/pt-lib/antlr-filestream")
    public static class UnsafeANTLRFileStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            @SuppressWarnings("deprecation")
            org.antlr.runtime.ANTLRFileStream stream = new org.antlr.runtime.ANTLRFileStream("/var/data/" + fileName);
            response.getWriter().println("size: " + stream.size());
        }
    }

    // ── Apache Ant AntClassLoader ───────────────────────────────────────────

    @WebServlet("/pt-lib/ant-classloader")
    public static class UnsafeAntClassLoaderServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String pathComponent = request.getParameter("path");
            File file = new File("/var/lib/" + pathComponent);
            org.apache.tools.ant.AntClassLoader cl = new org.apache.tools.ant.AntClassLoader();
            cl.addPathComponent(file);
        }
    }

    // ── Apache Ant DirectoryScanner.setBasedir ──────────────────────────────

    @WebServlet("/pt-lib/ant-dirscanner")
    public static class UnsafeAntDirectoryScannerServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            org.apache.tools.ant.DirectoryScanner ds = new org.apache.tools.ant.DirectoryScanner();
            ds.setBasedir(dir);
        }
    }

    // ── Apache Ant Copy.setFile ─────────────────────────────────────────────

    @WebServlet("/pt-lib/ant-copy-setfile")
    public static class UnsafeAntCopySetFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.tools.ant.taskdefs.Copy copy = new org.apache.tools.ant.taskdefs.Copy();
            copy.setFile(file);
        }
    }

    // ── Apache Ant Copy.setTodir ────────────────────────────────────────────

    @WebServlet("/pt-lib/ant-copy-settodir")
    public static class UnsafeAntCopySetTodirServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            org.apache.tools.ant.taskdefs.Copy copy = new org.apache.tools.ant.taskdefs.Copy();
            copy.setTodir(dir);
        }
    }

    // ── Apache Ant Copy.setTofile ───────────────────────────────────────────

    @WebServlet("/pt-lib/ant-copy-settofile")
    public static class UnsafeAntCopySetTofileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.tools.ant.taskdefs.Copy copy = new org.apache.tools.ant.taskdefs.Copy();
            copy.setTofile(file);
        }
    }

    // ── Apache Ant Expand.setDest ───────────────────────────────────────────

    @WebServlet("/pt-lib/ant-expand-setdest")
    public static class UnsafeAntExpandSetDestServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            org.apache.tools.ant.taskdefs.Expand expand = new org.apache.tools.ant.taskdefs.Expand();
            expand.setDest(dir);
        }
    }

    // ── Apache Ant Expand.setSrc ────────────────────────────────────────────

    @WebServlet("/pt-lib/ant-expand-setsrc")
    public static class UnsafeAntExpandSetSrcServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.tools.ant.taskdefs.Expand expand = new org.apache.tools.ant.taskdefs.Expand();
            expand.setSrc(file);
        }
    }

    // ── Apache Ant Property.setFile ─────────────────────────────────────────

    @WebServlet("/pt-lib/ant-property-setfile")
    public static class UnsafeAntPropertySetFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.tools.ant.taskdefs.Property prop = new org.apache.tools.ant.taskdefs.Property();
            prop.setFile(file);
        }
    }

    // ── Apache Ant Property.setResource ─────────────────────────────────────

    @WebServlet("/pt-lib/ant-property-setresource")
    public static class UnsafeAntPropertySetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            org.apache.tools.ant.taskdefs.Property prop = new org.apache.tools.ant.taskdefs.Property();
            prop.setResource(resource);
        }
    }

    // ── Kotlin FilesKt.readText ─────────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-readtext")
    public static class UnsafeKotlinReadTextServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            String content = kotlin.io.FilesKt.readText(file, java.nio.charset.Charset.defaultCharset());
            response.getWriter().println(content);
        }
    }

    // ── Kotlin FilesKt.readBytes ────────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-readbytes")
    public static class UnsafeKotlinReadBytesServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            byte[] data = kotlin.io.FilesKt.readBytes(file);
            response.getOutputStream().write(data);
        }
    }

    // ── Kotlin FilesKt.writeText ────────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-writetext")
    public static class UnsafeKotlinWriteTextServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            kotlin.io.FilesKt.writeText(file, "data", java.nio.charset.Charset.defaultCharset());
        }
    }

    // ── Kotlin FilesKt.writeBytes ───────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-writebytes")
    public static class UnsafeKotlinWriteBytesServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            kotlin.io.FilesKt.writeBytes(file, "data".getBytes());
        }
    }

    // ── Kotlin FilesKt.appendText ───────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-appendtext")
    public static class UnsafeKotlinAppendTextServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            kotlin.io.FilesKt.appendText(file, "data", java.nio.charset.Charset.defaultCharset());
        }
    }

    // ── Kotlin FilesKt.appendBytes ──────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-appendbytes")
    public static class UnsafeKotlinAppendBytesServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            kotlin.io.FilesKt.appendBytes(file, "data".getBytes());
        }
    }

    // ── Kotlin FilesKt.deleteRecursively ────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-deleterecursively")
    public static class UnsafeKotlinDeleteRecursivelyServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            response.getWriter().println("deleted: " + kotlin.io.FilesKt.deleteRecursively(dir));
        }
    }

    // ── Kotlin FilesKt.copyTo ───────────────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-copyto")
    public static class UnsafeKotlinCopyToServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destFile = new File("/var/data/" + dest);
            kotlin.io.FilesKt.copyTo(new File("/var/data/source.dat"), destFile, false, 8192);
        }
    }

    // ── Kotlin FilesKt.copyRecursively ──────────────────────────────────────

    @WebServlet("/pt-lib/kotlin-copyrecursively")
    public static class UnsafeKotlinCopyRecursivelyServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach kotlin.io.FilesKt sink argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            kotlin.io.FilesKt.copyRecursively(new File("/var/data/source"), destDir, false, (f, e) -> kotlin.io.OnErrorAction.SKIP);
        }
    }

    // ── JMH ChainedOptionsBuilder.result ────────────────────────────────────

    @WebServlet("/pt-lib/jmh-result")
    public static class UnsafeJmhResultServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            org.openjdk.jmh.runner.options.OptionsBuilder builder = new org.openjdk.jmh.runner.options.OptionsBuilder();
            builder.result("/var/data/" + fileName);
        }
    }

    // ── Netty OpenSslServerContext constructor ────────────────────────────────

    @WebServlet("/pt-lib/netty-opensslserverctx")
    public static class UnsafeOpenSslServerContextServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String certPath = request.getParameter("cert");
            File certFile = new File("/var/ssl/" + certPath);
            try {
                @SuppressWarnings("deprecation")
                io.netty.handler.ssl.OpenSslServerContext ctx =
                        new io.netty.handler.ssl.OpenSslServerContext(certFile, new File("/var/ssl/key.pem"));
            } catch (javax.net.ssl.SSLException e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }

    // ── Ant Copy.addFileset ──────────────────────────────────────────────────

    @WebServlet("/pt-lib/ant-copy-addfileset")
    public static class UnsafeAntCopyAddFilesetServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not propagate through intermediate FileSet object; re-enable when summaries are added
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            org.apache.tools.ant.types.FileSet fs = new org.apache.tools.ant.types.FileSet();
            fs.setDir(new File("/var/data/" + dirName));
            org.apache.tools.ant.taskdefs.Copy copy = new org.apache.tools.ant.taskdefs.Copy();
            copy.addFileset(fs);
        }
    }
}
