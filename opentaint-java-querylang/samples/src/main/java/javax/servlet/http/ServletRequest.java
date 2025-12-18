package javax.servlet.http;

public interface ServletRequest {
    String getParameter(String name);

    String getHeader(String name);

    HttpSession getSession();
}
