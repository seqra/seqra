package security.commandinjection;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.seqra.sast.test.util.PositiveRuleSample;

/**
 * Samples for os-command-injection-in-servlet.
 */
public class CommandInjectionServletSamples {

    /**
     * Unsafe servlet that concatenates untrusted request parameters into an OS command
     * executed via Runtime.exec.
     */
    @WebServlet("/os-command-injection-in-servlet/unsafe")
    public static class UnsafeCommandServlet extends HttpServlet {

        @Override
        @PositiveRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String host = request.getParameter("host"); // untrusted input

            // VULNERABLE: directly concatenating untrusted input into an OS command string
            String command = "ping -c 4 " + host;

            try {
                Process process = Runtime.getRuntime().exec(command);
                // In a real application, the output would be streamed back to the client.
                PrintWriter out = response.getWriter();
                out.println("Started ping for host: " + host + ", process: " + process);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }


    /**
     * Safe servlet that validates the host and avoids shell interpretation by using
     * a ProcessBuilder with separated arguments.
     */
    @WebServlet("/os-command-injection-in-servlet/safe")
    public static class SafeCommandServlet extends HttpServlet {

        @Override
// TODO: restore this when conditional validators are implemented
//        @NegativeRuleSample(value = "java/security/command-injection.yaml", id = "os-command-injection-in-servlet-app")
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            String host = request.getParameter("host");

            // Basic validation: allow only simple hostnames / IPs with restricted characters
            if (host == null || !host.matches("^[a-zA-Z0-9._-]{1,255}$")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid host");
                return;
            }

            try {
                // SAFE: do not invoke a shell, use arguments list instead
                ProcessBuilder pb = new ProcessBuilder("ping", "-c", "4", host);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                PrintWriter out = response.getWriter();
                out.println("Started safe ping for host: " + host + ", process: " + process);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }
}
