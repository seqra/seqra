package javax.servlet.http;

public interface HttpServletResponse {
    void addCookie(Cookie cookie);

    static HttpServletResponse create() {
        return new Impl();
    }

    class Impl implements HttpServletResponse {

        private Cookie cookie;

        @Override
        public void addCookie(Cookie cookie) {
            this.cookie = cookie;
        }
    }
}
