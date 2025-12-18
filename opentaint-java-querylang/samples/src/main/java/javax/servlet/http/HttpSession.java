package javax.servlet.http;

public interface HttpSession {
    void setAttribute(String key, String value);

    class Impl implements HttpSession {
        @Override
        public void setAttribute(String key, String value) {

        }
    }
}
