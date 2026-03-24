package security.pathtraversal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based test samples covering Apache Commons IO sink patterns for path traversal:
 * FileUtils, IOUtils, PathUtils, and output writer constructors.
 */
public class PathTraversalCommonsIoSinksSamples {

    // ── FileUtils.cleanDirectory ────────────────────────────────────────────

    @WebServlet("/pt-cio/cleandirectory")
    public static class UnsafeCleanDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            FileUtils.cleanDirectory(dir);
        }
    }

    // ── FileUtils.copyDirectory ─────────────────────────────────────────────

    @WebServlet("/pt-cio/copydirectory")
    public static class UnsafeCopyDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.copyDirectory(new File("/var/data/source"), destDir);
        }
    }

    // ── FileUtils.copyDirectoryToDirectory ──────────────────────────────────

    @WebServlet("/pt-cio/copydirtodirectory")
    public static class UnsafeCopyDirToDirServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.copyDirectoryToDirectory(new File("/var/data/source"), destDir);
        }
    }

    // ── FileUtils.copyFile ──────────────────────────────────────────────────

    @WebServlet("/pt-cio/copyfile")
    public static class UnsafeCopyFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destFile = new File("/var/data/" + dest);
            FileUtils.copyFile(new File("/var/data/source.dat"), destFile);
        }
    }

    // ── FileUtils.copyFileToDirectory ───────────────────────────────────────

    @WebServlet("/pt-cio/copyfiletodirectory")
    public static class UnsafeCopyFileToDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.copyFileToDirectory(new File("/var/data/source.dat"), destDir);
        }
    }

    // ── FileUtils.copyToDirectory ───────────────────────────────────────────

    @WebServlet("/pt-cio/copytodirectory")
    public static class UnsafeCopyToDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.copyToDirectory(new File("/var/data/source.dat"), destDir);
        }
    }

    // ── FileUtils.copyToFile ────────────────────────────────────────────────

    @WebServlet("/pt-cio/copytofile")
    public static class UnsafeCopyToFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destFile = new File("/var/data/" + dest);
            FileUtils.copyToFile(request.getInputStream(), destFile);
        }
    }

    // ── FileUtils.copyURLToFile ─────────────────────────────────────────────

    @WebServlet("/pt-cio/copyurltofile")
    public static class UnsafeCopyURLToFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destFile = new File("/var/data/" + dest);
            FileUtils.copyURLToFile(new java.net.URL("http://example.com/data"), destFile);
        }
    }

    // ── FileUtils.delete ────────────────────────────────────────────────────

    @WebServlet("/pt-cio/delete")
    public static class UnsafeDeleteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.delete(file);
        }
    }

    // ── FileUtils.deleteDirectory ───────────────────────────────────────────

    @WebServlet("/pt-cio/deletedirectory")
    public static class UnsafeDeleteDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            FileUtils.deleteDirectory(dir);
        }
    }

    // ── FileUtils.deleteQuietly ─────────────────────────────────────────────

    @WebServlet("/pt-cio/deletequietly")
    public static class UnsafeDeleteQuietlyServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.deleteQuietly(file);
        }
    }

    // ── FileUtils.forceDelete ───────────────────────────────────────────────

    @WebServlet("/pt-cio/forcedelete")
    public static class UnsafeForceDeleteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.forceDelete(file);
        }
    }

    // ── FileUtils.forceDeleteOnExit ─────────────────────────────────────────

    @WebServlet("/pt-cio/forcedeleteonexit")
    public static class UnsafeForceDeleteOnExitServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.forceDeleteOnExit(file);
        }
    }

    // ── FileUtils.forceMkdirParent ──────────────────────────────────────────

    @WebServlet("/pt-cio/forcemkdirparent")
    public static class UnsafeForceMkdirParentServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.forceMkdirParent(file);
        }
    }

    // ── FileUtils.iterateFiles ──────────────────────────────────────────────

    @WebServlet("/pt-cio/iteratefiles")
    public static class UnsafeIterateFilesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            FileUtils.iterateFiles(dir, null, true);
        }
    }

    // ── FileUtils.iterateFilesAndDirs ───────────────────────────────────────

    @WebServlet("/pt-cio/iteratefilesanddirs")
    public static class UnsafeIterateFilesAndDirsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            FileUtils.iterateFilesAndDirs(dir, org.apache.commons.io.filefilter.TrueFileFilter.TRUE,
                    org.apache.commons.io.filefilter.TrueFileFilter.TRUE);
        }
    }

    // ── FileUtils.listFiles ─────────────────────────────────────────────────

    @WebServlet("/pt-cio/listfiles")
    public static class UnsafeListFilesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            response.getWriter().println("count: " + FileUtils.listFiles(dir, null, true).size());
        }
    }

    // ── FileUtils.listFilesAndDirs ──────────────────────────────────────────

    @WebServlet("/pt-cio/listfilesanddirs")
    public static class UnsafeListFilesAndDirsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            response.getWriter().println("count: " + FileUtils.listFilesAndDirs(dir,
                    org.apache.commons.io.filefilter.TrueFileFilter.TRUE,
                    org.apache.commons.io.filefilter.TrueFileFilter.TRUE).size());
        }
    }

    // ── FileUtils.moveDirectory ─────────────────────────────────────────────

    @WebServlet("/pt-cio/movedirectory")
    public static class UnsafeMoveDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.moveDirectory(new File("/var/data/source"), destDir);
        }
    }

    // ── FileUtils.moveDirectoryToDirectory ──────────────────────────────────

    @WebServlet("/pt-cio/movedirtodirectory")
    public static class UnsafeMoveDirToDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.moveDirectoryToDirectory(new File("/var/data/source"), destDir, true);
        }
    }

    // ── FileUtils.moveFile ──────────────────────────────────────────────────

    @WebServlet("/pt-cio/movefile")
    public static class UnsafeMoveFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destFile = new File("/var/data/" + dest);
            FileUtils.moveFile(new File("/var/data/source.dat"), destFile);
        }
    }

    // ── FileUtils.moveFileToDirectory ───────────────────────────────────────

    @WebServlet("/pt-cio/movefiletodirectory")
    public static class UnsafeMoveFileToDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.moveFileToDirectory(new File("/var/data/source.dat"), destDir, true);
        }
    }

    // ── FileUtils.moveToDirectory ───────────────────────────────────────────

    @WebServlet("/pt-cio/movetodirectory")
    public static class UnsafeMoveToDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destDir = new File("/var/data/" + dest);
            FileUtils.moveToDirectory(new File("/var/data/source.dat"), destDir, true);
        }
    }

    // ── FileUtils.openOutputStream ──────────────────────────────────────────

    @WebServlet("/pt-cio/openoutputstream")
    public static class UnsafeOpenOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            java.io.OutputStream os = FileUtils.openOutputStream(file);
            os.close();
        }
    }

    // ── FileUtils.openInputStream ───────────────────────────────────────────

    @WebServlet("/pt-cio/openinputstream")
    public static class UnsafeOpenInputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            java.io.InputStream is = FileUtils.openInputStream(file);
            is.close();
        }
    }

    // ── FileUtils.readFileToByteArray ───────────────────────────────────────

    @WebServlet("/pt-cio/readfiletobytearray")
    public static class UnsafeReadFileToByteArrayServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getOutputStream().write(FileUtils.readFileToByteArray(file));
        }
    }

    // ── FileUtils.readFileToString ──────────────────────────────────────────

    @WebServlet("/pt-cio/readfiletostring")
    public static class UnsafeReadFileToStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println(FileUtils.readFileToString(file, "UTF-8"));
        }
    }

    // ── FileUtils.readLines ─────────────────────────────────────────────────

    @WebServlet("/pt-cio/readlines")
    public static class UnsafeReadLinesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("lines: " + FileUtils.readLines(file, "UTF-8").size());
        }
    }

    // ── FileUtils.touch ─────────────────────────────────────────────────────

    @WebServlet("/pt-cio/touch")
    public static class UnsafeTouchServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.touch(file);
        }
    }

    // ── FileUtils.write ─────────────────────────────────────────────────────

    @WebServlet("/pt-cio/write")
    public static class UnsafeWriteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.write(file, "data", "UTF-8");
        }
    }

    // ── FileUtils.writeByteArrayToFile ──────────────────────────────────────

    @WebServlet("/pt-cio/writebytearraytofile")
    public static class UnsafeWriteByteArrayToFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.writeByteArrayToFile(file, "data".getBytes());
        }
    }

    // ── FileUtils.writeLines ────────────────────────────────────────────────

    @WebServlet("/pt-cio/writelines")
    public static class UnsafeWriteLinesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.writeLines(file, java.util.Collections.singletonList("line"));
        }
    }

    // ── FileUtils.writeStringToFile ─────────────────────────────────────────

    @WebServlet("/pt-cio/writestringtofile")
    public static class UnsafeWriteStringToFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            FileUtils.writeStringToFile(file, "data", "UTF-8");
        }
    }

    // ── FileUtils.streamFiles ───────────────────────────────────────────────

    @WebServlet("/pt-cio/streamfiles")
    public static class UnsafeStreamFilesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            response.getWriter().println("count: " + FileUtils.streamFiles(dir, true).count());
        }
    }

    // ── FileUtils.newOutputStream ───────────────────────────────────────────

    @WebServlet("/pt-cio/fu-newoutputstream")
    public static class UnsafeFileUtilsNewOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            java.io.OutputStream os = FileUtils.newOutputStream(file, false);
            os.close();
        }
    }

    // ── IOUtils.copy(InputStream, File) ─────────────────────────────────────

    @WebServlet("/pt-cio/ioutils-copy")
    public static class UnsafeIOUtilsCopyServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            File destFile = new File("/var/data/" + dest);
            org.apache.commons.io.IOUtils.copy(request.getInputStream(), new java.io.FileOutputStream(destFile));
        }
    }

    // ── IOUtils.resourceToString ────────────────────────────────────────────

    @WebServlet("/pt-cio/ioutils-resourcetostring")
    public static class UnsafeIOUtilsResourceToStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            String content = org.apache.commons.io.IOUtils.resourceToString(resource, java.nio.charset.StandardCharsets.UTF_8);
            response.getWriter().println(content);
        }
    }

    // ── RandomAccessFileMode.create ─────────────────────────────────────────

    @WebServlet("/pt-cio/randomaccessfilemode-create")
    public static class UnsafeRandomAccessFileModeServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            java.io.RandomAccessFile raf = org.apache.commons.io.RandomAccessFileMode.READ_ONLY.create(file);
            raf.close();
        }
    }

    // ── PathUtils.copyFile ──────────────────────────────────────────────────

    @WebServlet("/pt-cio/pathutils-copyfile")
    public static class UnsafePathUtilsCopyFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            Path destPath = Paths.get("/var/data/" + dest);
            org.apache.commons.io.file.PathUtils.copyFile(Paths.get("/var/data/source.dat").toUri().toURL(), destPath);
        }
    }

    // ── PathUtils.copyFileToDirectory ───────────────────────────────────────

    @WebServlet("/pt-cio/pathutils-copyfiletodirectory")
    public static class UnsafePathUtilsCopyFileToDirServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dest = request.getParameter("dest");
            Path destDir = Paths.get("/var/data/" + dest);
            org.apache.commons.io.file.PathUtils.copyFileToDirectory(Paths.get("/var/data/source.dat"), destDir);
        }
    }

    // ── PathUtils.newOutputStream ───────────────────────────────────────────

    @WebServlet("/pt-cio/pathutils-newoutputstream")
    public static class UnsafePathUtilsNewOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            java.io.OutputStream os = org.apache.commons.io.file.PathUtils.newOutputStream(path, false);
            os.close();
        }
    }

    // ── PathUtils.writeString ───────────────────────────────────────────────

    @WebServlet("/pt-cio/pathutils-writestring")
    public static class UnsafePathUtilsWriteStringServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            Path path = Paths.get("/var/data/" + fileName);
            org.apache.commons.io.file.PathUtils.writeString(path, "data", java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ── LockableFileWriter ──────────────────────────────────────────────────

    @WebServlet("/pt-cio/lockablefilewriter")
    public static class UnsafeLockableFileWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.commons.io.output.LockableFileWriter writer =
                    new org.apache.commons.io.output.LockableFileWriter(file);
            writer.write("data");
            writer.close();
        }
    }

    // ── XmlStreamWriter ─────────────────────────────────────────────────────

    @WebServlet("/pt-cio/xmlstreamwriter")
    public static class UnsafeXmlStreamWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            org.apache.commons.io.output.XmlStreamWriter writer =
                    new org.apache.commons.io.output.XmlStreamWriter(new java.io.FileOutputStream(file));
            writer.write("data");
            writer.close();
        }
    }

    // ── Commons Net KeyManagerUtils.createClientKeyManager(File,...) ────────

    @WebServlet("/pt-cio/keymgr-file")
    public static class UnsafeKeyManagerUtilsFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            try {
                org.apache.commons.net.util.KeyManagerUtils.createClientKeyManager(file, "changeit");
            } catch (Exception e) {
                response.getWriter().println("error: " + e.getMessage());
            }
        }
    }
}
