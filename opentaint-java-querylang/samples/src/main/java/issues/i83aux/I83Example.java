package issues.i83aux;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.ServletRequest;

public class I83Example {
    public String doGet(HttpServletRequest req) {
        final ServletRequest r = req;
        final String param = r.getParameter("param");
        return useParam(param);
    }

    private String useParam(String param) {
        return param;
    }
}
