package custom.logInjection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;


@RestController
@RequestMapping("/logInject")
public class LogInjection {
    private static final Logger logger = LoggerFactory.getLogger(LogInjection.class);
    private static final String EXCEPT = "log injection except";

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private static String getRequestBody(HttpServletRequest request) throws IOException {
        InputStream in = request.getInputStream();
        return convertStreamToString(in);
    }

    @RequestMapping(value = "/LogInjection/vuln", method = RequestMethod.POST)
    public String LogInjectionVuln(HttpServletRequest request) {
        try {
            String body = getRequestBody(request);
            logger.info(body);

            return "vuln code";
        } catch (Exception e) {
            return EXCEPT;
        }
    }
}
