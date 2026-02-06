package security.pathtraversal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Servlet-based samples for path-traversal-in-servlet rule.
 *
 * Multiple variants are provided to exercise different untrusted sources (query parameters,
 * headers, cookies) and different file/Path-based sinks.
 */
public class PathTraversalServletSamples {

    /**
     * VULNERABLE: directly concatenates untrusted query parameter into a filesystem path.
     */
    @WebServlet("/pathtraversal/unsafe-download-param")
    public static class UnsafeParamDownloadServlet extends HttpServlet {

        private static final String BASE_DIR = "/var/www/uploads/";

        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String fileName = request.getParameter("file");

            // VULNERABLE: user-controlled value is concatenated directly into the path
            File file = new File(BASE_DIR + fileName);

            if (!file.exists() || !file.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            streamFile(response, file);
        }
    }

    /**
     * VULNERABLE: uses an HTTP header as the source of the filename and builds a java.nio.file.Path
     * directly from tainted data.
     */
    @WebServlet("/pathtraversal/unsafe-download-header")
    public static class UnsafeHeaderDownloadServlet extends HttpServlet {

        private static final String BASE_DIR = "/var/www/uploads/";

        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String headerName = request.getHeader("X-Download-File");
            if (headerName == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Download-File header");
                return;
            }

            // VULNERABLE: untrusted header value is directly concatenated into a path
            Path path = Paths.get(BASE_DIR + headerName);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            streamPath(response, path);
        }
    }

    /**
     * VULNERABLE: reads filename from a cookie and uses it directly as a File child of BASE_DIR
     * without validation or canonicalization.
     */
    @WebServlet("/pathtraversal/unsafe-download-cookie")
    public static class UnsafeCookieDownloadServlet extends HttpServlet {

        private static final File BASE_DIR = new File("/var/www/uploads");

        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String fileName = null;
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("download".equals(cookie.getName())) {
                        fileName = cookie.getValue();
                        break;
                    }
                }
            }

            if (fileName == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing download cookie");
                return;
            }

            // VULNERABLE: cookie-controlled file name with no checks
            File file = new File(BASE_DIR, fileName);

            if (!file.exists() || !file.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            streamFile(response, file);
        }
    }

    /**
     * SAFE: validates and normalizes the path, enforcing a fixed base directory for query parameter
     * driven downloads.
     */
    @WebServlet("/pathtraversal/safe-download-param-1")
    public static class SafeParamDownloadServlet1 extends HttpServlet {

        private static final File BASE_DIR = new File("/var/www/uploads").getAbsoluteFile();

        @Override
//    TODO: enable this test when we have conditional sanitizers
//        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String fileName = request.getParameter("file");
            if (fileName == null || fileName.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing file parameter");
                return;
            }

            // Basic allowlist-style validation: only simple filenames allowed
            if (!fileName.matches("[A-Za-z0-9._-]+")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid file name");
                return;
            }

            File target = new File(BASE_DIR, fileName).getCanonicalFile();

            // Ensure the resolved path is still under BASE_DIR
            if (!target.toPath().startsWith(BASE_DIR.toPath())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }

            if (!target.exists() || !target.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            streamFile(response, target);
        }
    }

  /**
   * SAFE: validates and normalizes the path, enforcing a fixed base directory for query parameter
   * driven downloads.
   */
    @WebServlet("/pathtraversal/safe-download-param-2")
    public static class SafeParamDownloadServlet2 extends HttpServlet {

        private static final File BASE_DIR = new File("/var/www/uploads").getAbsoluteFile();

        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String fileName = request.getParameter("file");
            if (fileName == null || fileName.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing file parameter");
                return;
            }

            fileName = org.apache.commons.io.FilenameUtils.getName(fileName);

            File target = new File(BASE_DIR, fileName).getCanonicalFile();

            if (!target.exists() || !target.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            streamFile(response, target);
        }
    }

    /**
     * SAFE: reads desired file name from a header but only serves from a small hard-coded
     * allowlist, avoiding arbitrary path construction.
     */
    @WebServlet("/pathtraversal/safe-download-header")
    public static class SafeHeaderDownloadServlet extends HttpServlet {

        private static final File BASE_DIR = new File("/var/www/uploads").getAbsoluteFile();

        @Override
        @NegativeRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String headerName = request.getHeader("X-Download-File");
            if (headerName == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Download-File header");
                return;
            }

            // Allowlist: map logical names to concrete files
            File target;
            if ("report".equals(headerName)) {
                target = new File(BASE_DIR, "monthly-report.pdf");
            } else if ("invoice".equals(headerName)) {
                target = new File(BASE_DIR, "latest-invoice.pdf");
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown resource");
                return;
            }

            target = target.getCanonicalFile();

            if (!target.toPath().startsWith(BASE_DIR.toPath())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }

            if (!target.exists() || !target.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            streamFile(response, target);
        }
    }

    /**
     * VULNERABLE: uses filename from Apache Commons FileUpload directly in path construction.
     */
    @WebServlet("/pathtraversal/unsafe-fileupload")
    public static class UnsafeFileUploadServlet extends HttpServlet {

        private static final String UPLOAD_DIR = "/var/www/uploads/";

        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            ServletFileUpload upload = new ServletFileUpload();
            try {
                // Parse file upload request - untrusted source
                List<FileItem> files = upload.parseRequest(request);

                for (FileItem file : files) {
                    String fileName = file.getName();

                    // Path traversal vulnerability: attacker can upload file with name like "../../etc/passwd"
                    File targetFile = new File(UPLOAD_DIR + fileName);

                    streamFile(response, targetFile);
                }
            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Helper method to stream a java.io.File to the servlet response as octet-stream.
     */
    private static void streamFile(HttpServletResponse response, File file) throws IOException {
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

        try (FileInputStream fis = new FileInputStream(file);
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * Helper method to stream a java.nio.file.Path to the servlet response.
     */
    private static void streamPath(HttpServletResponse response, Path path) throws IOException {
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + path.getFileName().toString() + "\"");

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(path, out);
        }
    }
}
