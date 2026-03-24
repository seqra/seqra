package security.pathtraversal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based test samples covering java.nio.file.Files and FileSystems
 * sink patterns for path traversal.
 */
public class PathTraversalNioSinksSamples {

    // ── Files.createDirectories ─────────────────────────────────────────────

    @WebServlet("/pt-nio/createdirectories")
    public static class UnsafeCreateDirectoriesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path path = Paths.get("/var/data/" + dirName);
            Files.createDirectories(path);
        }
    }

    // ── Files.createDirectory ───────────────────────────────────────────────

    @WebServlet("/pt-nio/createdirectory")
    public static class UnsafeCreateDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path path = Paths.get("/var/data/" + dirName);
            Files.createDirectory(path);
        }
    }

    // ── Files.createFile ────────────────────────────────────────────────────

    @WebServlet("/pt-nio/createfile")
    public static class UnsafeCreateFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.createFile(path);
        }
    }

    // ── Files.createLink ────────────────────────────────────────────────────

    @WebServlet("/pt-nio/createlink")
    public static class UnsafeCreateLinkServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String linkName = request.getParameter("link");
            Path link = Paths.get("/var/data/" + linkName);
            Files.createLink(Paths.get("/var/data/existing"), link);
        }
    }

    // ── Files.createSymbolicLink ────────────────────────────────────────────

    @WebServlet("/pt-nio/createsymlink")
    public static class UnsafeCreateSymlinkServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String linkName = request.getParameter("link");
            Path link = Paths.get("/var/data/" + linkName);
            Files.createSymbolicLink(Paths.get("/var/data/target"), link);
        }
    }

    // ── Files.createTempFile ────────────────────────────────────────────────

    @WebServlet("/pt-nio/createtempfile")
    public static class UnsafeCreateTempFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path dir = Paths.get("/var/tmp/" + dirName);
            Files.createTempFile(dir, "prefix", ".tmp");
        }
    }

    // ── Files.createTempDirectory ───────────────────────────────────────────

    @WebServlet("/pt-nio/createtempdir")
    public static class UnsafeCreateTempDirServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path dir = Paths.get("/var/tmp/" + dirName);
            Files.createTempDirectory(dir, "prefix");
        }
    }

    // ── Files.delete ────────────────────────────────────────────────────────

    @WebServlet("/pt-nio/delete")
    public static class UnsafeDeleteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.delete(path);
        }
    }

    // ── Files.deleteIfExists ────────────────────────────────────────────────

    @WebServlet("/pt-nio/deleteifexists")
    public static class UnsafeDeleteIfExistsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.deleteIfExists(path);
        }
    }

    // ── Files.find ──────────────────────────────────────────────────────────

    @WebServlet("/pt-nio/find")
    public static class UnsafeFindServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path dir = Paths.get("/var/data/" + dirName);
            Stream<Path> found = Files.find(dir, 3, (p, a) -> true);
            response.getWriter().println("count: " + found.count());
        }
    }

    // ── Files.getFileStore ──────────────────────────────────────────────────

    @WebServlet("/pt-nio/getfilestore")
    public static class UnsafeGetFileStoreServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            response.getWriter().println("store: " + Files.getFileStore(path));
        }
    }

    // ── Files.move ──────────────────────────────────────────────────────────

    @WebServlet("/pt-nio/move")
    public static class UnsafeMoveServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String destName = request.getParameter("dest");
            Path dest = Paths.get("/var/data/" + destName);
            Files.move(Paths.get("/var/data/source.dat"), dest);
        }
    }

    // ── Files.newBufferedReader ──────────────────────────────────────────────

    @WebServlet("/pt-nio/newbufferedreader")
    public static class UnsafeNewBufferedReaderServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            java.io.BufferedReader br = Files.newBufferedReader(path);
            br.close();
        }
    }

    // ── Files.newBufferedWriter ─────────────────────────────────────────────

    @WebServlet("/pt-nio/newbufferedwriter")
    public static class UnsafeNewBufferedWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            java.io.BufferedWriter bw = Files.newBufferedWriter(path);
            bw.close();
        }
    }

    // ── Files.newByteChannel ────────────────────────────────────────────────

    @WebServlet("/pt-nio/newbytechannel")
    public static class UnsafeNewByteChannelServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            java.nio.channels.SeekableByteChannel ch = Files.newByteChannel(path);
            ch.close();
        }
    }

    // ── Files.newDirectoryStream ────────────────────────────────────────────

    @WebServlet("/pt-nio/newdirectorystream")
    public static class UnsafeNewDirectoryStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path dir = Paths.get("/var/data/" + dirName);
            java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir);
            ds.close();
        }
    }

    // ── Files.newInputStream ────────────────────────────────────────────────

    @WebServlet("/pt-nio/newinputstream")
    public static class UnsafeNewInputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            java.io.InputStream is = Files.newInputStream(path);
            is.close();
        }
    }

    // ── Files.newOutputStream ───────────────────────────────────────────────

    @WebServlet("/pt-nio/newoutputstream")
    public static class UnsafeNewOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            java.io.OutputStream os = Files.newOutputStream(path);
            os.close();
        }
    }

    // ── Files.notExists ─────────────────────────────────────────────────────

    @WebServlet("/pt-nio/notexists")
    public static class UnsafeNotExistsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            response.getWriter().println("notExists: " + Files.notExists(path));
        }
    }

    // ── Files.probeContentType ──────────────────────────────────────────────

    @WebServlet("/pt-nio/probecontenttype")
    public static class UnsafeProbeContentTypeServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            response.getWriter().println("type: " + Files.probeContentType(path));
        }
    }

    // ── Files.readAllLines ──────────────────────────────────────────────────

    @WebServlet("/pt-nio/readalllines")
    public static class UnsafeReadAllLinesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            response.getWriter().println("lines: " + Files.readAllLines(path).size());
        }
    }

    // ── Files.readSymbolicLink ──────────────────────────────────────────────

    @WebServlet("/pt-nio/readsymboliclink")
    public static class UnsafeReadSymbolicLinkServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String linkName = request.getParameter("link");
            Path path = Paths.get("/var/data/" + linkName);
            response.getWriter().println("target: " + Files.readSymbolicLink(path));
        }
    }

    // ── Files.setLastModifiedTime ───────────────────────────────────────────

    @WebServlet("/pt-nio/setlastmodifiedtime")
    public static class UnsafeSetLastModifiedTimeServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
        }
    }

    // ── Files.setOwner ──────────────────────────────────────────────────────

    @WebServlet("/pt-nio/setowner")
    public static class UnsafeSetOwnerServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.setOwner(path, null);
        }
    }

    // ── Files.setPosixFilePermissions ───────────────────────────────────────

    @WebServlet("/pt-nio/setposixpermissions")
    public static class UnsafeSetPosixPermissionsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Set<PosixFilePermission> perms = Collections.singleton(PosixFilePermission.OWNER_READ);
            Files.setPosixFilePermissions(path, perms);
        }
    }

    // ── Files.walk ──────────────────────────────────────────────────────────

    @WebServlet("/pt-nio/walk")
    public static class UnsafeWalkServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path dir = Paths.get("/var/data/" + dirName);
            response.getWriter().println("count: " + Files.walk(dir).count());
        }
    }

    // ── Files.walkFileTree ──────────────────────────────────────────────────

    @WebServlet("/pt-nio/walkfiletree")
    public static class UnsafeWalkFileTreeServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            Path dir = Paths.get("/var/data/" + dirName);
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<Path>() {});
        }
    }

    // ── Files.write ─────────────────────────────────────────────────────────

    @WebServlet("/pt-nio/write")
    public static class UnsafeWriteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.write(path, "data".getBytes(StandardCharsets.UTF_8));
        }
    }

    // ── Files.writeString ───────────────────────────────────────────────────

    @WebServlet("/pt-nio/writestring")
    public static class UnsafeWriteStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            Files.writeString(path, "data");
        }
    }

    // ── FileSystems.newFileSystem ────────────────────────────────────────────

    @WebServlet("/pt-nio/newfilesystem")
    public static class UnsafeNewFileSystemServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            FileSystem fs = FileSystems.newFileSystem(path, (ClassLoader) null);
            fs.close();
        }
    }

    // ── FileSystems.getFileSystem ────────────────────────────────────────────

    @WebServlet("/pt-nio/getfilesystem")
    public static class UnsafeGetFileSystemServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String uri = request.getParameter("uri");
            java.net.URI fsUri = java.net.URI.create(uri);
            FileSystem fs = FileSystems.getFileSystem(fsUri);
            response.getWriter().println("fs: " + fs);
        }
    }
}
