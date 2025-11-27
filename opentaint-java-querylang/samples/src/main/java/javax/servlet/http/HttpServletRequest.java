package javax.servlet.http;

import java.io.InputStream;

public interface HttpServletRequest {
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
    }
}
