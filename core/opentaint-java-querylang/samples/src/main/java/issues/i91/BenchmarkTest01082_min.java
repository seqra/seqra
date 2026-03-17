package issues.i91;

import javax.servlet.http.HttpServletRequest;

public class BenchmarkTest01082_min {
    public void doPost(HttpServletRequest request) {
        String param = "";
        if (request.getHeader("BenchmarkTest01082") != null) {
            param = request.getHeader("BenchmarkTest01082");
        }
        request.getSession().setAttribute("10340", param);
    }
}
