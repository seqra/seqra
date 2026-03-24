package security.pathtraversal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based test samples covering java.io sink patterns for path traversal:
 * constructors (FileReader, FileWriter, FileOutputStream, RandomAccessFile),
 * File.createTempFile, and File instance methods.
 */
public class PathTraversalJavaIoSinksSamples {

    // ── java.io.FileReader ──────────────────────────────────────────────────

    @WebServlet("/pt-io/filereader")
    public static class UnsafeFileReaderServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            FileReader reader = new FileReader("/var/data/" + fileName);
            reader.close();
        }
    }

    // ── java.io.FileWriter ──────────────────────────────────────────────────

    @WebServlet("/pt-io/filewriter")
    public static class UnsafeFileWriterServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            FileWriter writer = new FileWriter("/var/data/" + fileName);
            writer.write("data");
            writer.close();
        }
    }

    // ── java.io.FileOutputStream ────────────────────────────────────────────

    @WebServlet("/pt-io/fileoutputstream")
    public static class UnsafeFileOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            FileOutputStream fos = new FileOutputStream("/var/data/" + fileName);
            fos.write(42);
            fos.close();
        }
    }

    // ── java.io.RandomAccessFile ────────────────────────────────────────────

    @WebServlet("/pt-io/randomaccessfile")
    public static class UnsafeRandomAccessFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            RandomAccessFile raf = new RandomAccessFile("/var/data/" + fileName, "r");
            raf.close();
        }
    }

    // ── java.io.File.createTempFile ─────────────────────────────────────────

    @WebServlet("/pt-io/createtempfile")
    public static class UnsafeCreateTempFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/tmp/" + dirName);
            File temp = File.createTempFile("prefix", ".tmp", dir);
            response.getWriter().println(temp.getAbsolutePath());
        }
    }

    // ── File instance methods ───────────────────────────────────────────────

    @WebServlet("/pt-io/canexecute")
    public static class UnsafeCanExecuteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("executable: " + file.canExecute());
        }
    }

    @WebServlet("/pt-io/canwrite")
    public static class UnsafeCanWriteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("writable: " + file.canWrite());
        }
    }

    @WebServlet("/pt-io/isdirectory")
    public static class UnsafeIsDirectoryServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("directory: " + file.isDirectory());
        }
    }

    @WebServlet("/pt-io/ishidden")
    public static class UnsafeIsHiddenServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("hidden: " + file.isHidden());
        }
    }

    @WebServlet("/pt-io/delete")
    public static class UnsafeDeleteServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            response.getWriter().println("deleted: " + file.delete());
        }
    }

    @WebServlet("/pt-io/deleteonexit")
    public static class UnsafeDeleteOnExitServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.deleteOnExit();
        }
    }

    @WebServlet("/pt-io/createnewfile")
    public static class UnsafeCreateNewFileServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.createNewFile();
        }
    }

    @WebServlet("/pt-io/mkdir")
    public static class UnsafeMkdirServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            dir.mkdir();
        }
    }

    @WebServlet("/pt-io/mkdirs")
    public static class UnsafeMkdirsServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String dirName = request.getParameter("dir");
            File dir = new File("/var/data/" + dirName);
            dir.mkdirs();
        }
    }

    @WebServlet("/pt-io/setexecutable")
    public static class UnsafeSetExecutableServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.setExecutable(true);
        }
    }

    @WebServlet("/pt-io/setlastmodified")
    public static class UnsafeSetLastModifiedServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.setLastModified(System.currentTimeMillis());
        }
    }

    @WebServlet("/pt-io/setreadable")
    public static class UnsafeSetReadableServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.setReadable(true);
        }
    }

    @WebServlet("/pt-io/setreadonly")
    public static class UnsafeSetReadOnlyServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.setReadOnly();
        }
    }

    @WebServlet("/pt-io/setwritable")
    public static class UnsafeSetWritableServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            file.setWritable(true);
        }
    }
}
