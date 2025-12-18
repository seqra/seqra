package javax.servlet.http;

import java.io.InputStream;

public interface HttpServletRequest extends ServletRequest {
    InputStream getInputStream();
    static HttpServletRequest create() {
        return new HttpServletRequest.Impl();
    }

    class Impl implements HttpServletRequest {

        private InputStream input;

        @Override
        public InputStream getInputStream() {
            return this.input;
        }

        @Override
        public String getParameter(String name) {
            return "value";
        }

        @Override
        public String getHeader(String name) {
            return "another value";
        }

        @Override
        public HttpSession getSession() { return new HttpSession.Impl(); }
    }
}
