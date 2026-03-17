package issues.i83;

import javax.servlet.http.HttpServletRequest;

public class BenchmarkTest00020_min {
    public void doPost(HttpServletRequest request) {
        String param = request.getParameter("param");
        consume("str literal" + param);
    }

    private void consume(String data) {
    }
}
