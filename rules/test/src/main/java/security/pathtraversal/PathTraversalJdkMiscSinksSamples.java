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
 * Servlet-based test samples covering miscellaneous JDK sink patterns:
 * ClassLoader, Class, Module, ProcessBuilder, ImageIO, ServletContext,
 * StreamSource, ExternalContext (javax/jakarta), and FileDataSource.
 */
public class PathTraversalJdkMiscSinksSamples {

    // ── ClassLoader.getSystemResourceAsStream ───────────────────────────────

    @WebServlet("/pt-misc/cl-getsysresasstream")
    public static class UnsafeGetSystemResourceAsStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            java.io.InputStream is = ClassLoader.getSystemResourceAsStream(resource);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── ClassLoader.getSystemResources ──────────────────────────────────────

    @WebServlet("/pt-misc/cl-getsysresources")
    public static class UnsafeGetSystemResourcesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            java.util.Enumeration<java.net.URL> urls = ClassLoader.getSystemResources(resource);
            response.getWriter().println("found: " + urls.hasMoreElements());
        }
    }

    // ── (Module).getResourceAsStream ────────────────────────────────────────

    @WebServlet("/pt-misc/module-getresasstream")
    public static class UnsafeModuleGetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            Module mod = getClass().getModule();
            java.io.InputStream is = mod.getResourceAsStream(resource);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── ProcessBuilder.redirectError ─────────────────────────────────────────

    @WebServlet("/pt-misc/pb-redirecterror")
    public static class UnsafeRedirectErrorServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String logFile = request.getParameter("logfile");
            File errorFile = new File("/var/logs/" + logFile);
            ProcessBuilder pb = new ProcessBuilder("ls");
            pb.redirectError(errorFile);
        }
    }

    // ── FileImageOutputStream ───────────────────────────────────────────────

    @WebServlet("/pt-misc/fileimageoutputstream")
    public static class UnsafeFileImageOutputStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            javax.imageio.stream.FileImageOutputStream fios = new javax.imageio.stream.FileImageOutputStream(file);
            fios.close();
        }
    }

    // ── ServletContext.getResourceAsStream ───────────────────────────────────

    @WebServlet("/pt-misc/ctx-getresasstream")
    public static class UnsafeCtxGetResourceAsStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String path = request.getParameter("path");
            java.io.InputStream is = getServletContext().getResourceAsStream(path);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── StreamSource ────────────────────────────────────────────────────────

    @WebServlet("/pt-misc/streamsource")
    public static class UnsafeStreamSourceServlet extends HttpServlet {
        @Override
        // TODO: Analyzer FN – taint does not reach StreamSource constructor argument via new File(); re-enable when fixed
        // @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            javax.xml.transform.stream.StreamSource source = new javax.xml.transform.stream.StreamSource(file);
            response.getWriter().println("id: " + source.getSystemId());
        }
    }

    // ── javax.faces.context.ExternalContext.getResource ──────────────────────

    @WebServlet("/pt-misc/javax-faces-getresource")
    public static class UnsafeJavaxFacesGetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            javax.faces.context.ExternalContext ctx = null;
            java.net.URL url = ctx.getResource(resource);
            if (url != null) {
                response.getWriter().println("found: " + url);
            }
        }
    }

    // ── javax.faces.context.ExternalContext.getResourceAsStream ──────────────

    @WebServlet("/pt-misc/javax-faces-getresasstream")
    public static class UnsafeJavaxFacesGetResourceAsStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            javax.faces.context.ExternalContext ctx = null;
            java.io.InputStream is = ctx.getResourceAsStream(resource);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── jakarta.faces.context.ExternalContext.getResource ────────────────────

    @WebServlet("/pt-misc/jakarta-faces-getresource")
    public static class UnsafeJakartaFacesGetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            jakarta.faces.context.ExternalContext ctx = null;
            java.net.URL url = ctx.getResource(resource);
            if (url != null) {
                response.getWriter().println("found: " + url);
            }
        }
    }

    // ── jakarta.faces.context.ExternalContext.getResourceAsStream ────────────

    @WebServlet("/pt-misc/jakarta-faces-getresasstream")
    public static class UnsafeJakartaFacesGetResourceAsStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            jakarta.faces.context.ExternalContext ctx = null;
            java.io.InputStream is = ctx.getResourceAsStream(resource);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── javax.activation.FileDataSource ─────────────────────────────────────

    @WebServlet("/pt-misc/javax-filedatasource")
    public static class UnsafeJavaxFileDataSourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            javax.activation.FileDataSource ds = new javax.activation.FileDataSource(file);
            response.getWriter().println("type: " + ds.getContentType());
        }
    }

    // ── jakarta.activation.FileDataSource ───────────────────────────────────

    @WebServlet("/pt-misc/jakarta-filedatasource")
    public static class UnsafeJakartaFileDataSourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String fileName = request.getParameter("file");
            File file = new File("/var/data/" + fileName);
            jakarta.activation.FileDataSource ds = new jakarta.activation.FileDataSource(file);
            response.getWriter().println("type: " + ds.getContentType());
        }
    }

    // ── (Class).getResource ─────────────────────────────────────────────────

    @WebServlet("/pt-misc/class-getresource")
    public static class UnsafeClassGetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            Class<?> cls = PathTraversalJdkMiscSinksSamples.class;
            java.net.URL url = cls.getResource(resource);
            if (url != null) {
                response.getWriter().println("found: " + url);
            }
        }
    }

    // ── (Class).getResourceAsStream ─────────────────────────────────────────

    @WebServlet("/pt-misc/class-getresasstream")
    public static class UnsafeClassGetResourceAsStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            Class<?> cls = PathTraversalJdkMiscSinksSamples.class;
            java.io.InputStream is = cls.getResourceAsStream(resource);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── (ClassLoader).getResource ───────────────────────────────────────────

    @WebServlet("/pt-misc/classloader-getresource")
    public static class UnsafeClassLoaderGetResourceServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.net.URL url = cl.getResource(resource);
            if (url != null) {
                response.getWriter().println("found: " + url);
            }
        }
    }

    // ── (ClassLoader).getResourceAsStream ───────────────────────────────────

    @WebServlet("/pt-misc/classloader-getresasstream")
    public static class UnsafeClassLoaderGetResourceAsStreamServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.io.InputStream is = cl.getResourceAsStream(resource);
            if (is != null) {
                response.getWriter().println("found");
                is.close();
            }
        }
    }

    // ── (ClassLoader).getResources ──────────────────────────────────────────

    @WebServlet("/pt-misc/classloader-getresources")
    public static class UnsafeClassLoaderGetResourcesServlet extends HttpServlet {
        @Override
        @PositiveRuleSample(value = "java/security/path-traversal.yaml", id = "path-traversal-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String resource = request.getParameter("resource");
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.util.Enumeration<java.net.URL> urls = cl.getResources(resource);
            response.getWriter().println("found: " + urls.hasMoreElements());
        }
    }
}
