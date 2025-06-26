package security.externalconfigurationcontrol;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Samples for the bean-injection rule.
 */
@WebServlet("/bean-injection/")
public class BeanInjectionSamples extends HttpServlet {

    public static class UserDto {
        private String username;
        private String email;
        private boolean admin; // should not be directly controlled by user input

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public boolean isAdmin() {
            return admin;
        }

        public void setAdmin(boolean admin) {
            this.admin = admin;
        }
    }

    /**
     * Positive sample: untrusted servlet parameter map directly populates a bean.
     */
    @PositiveRuleSample(value = "java/security/external-configuration-control.yaml", id = "bean-injection")
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse resp) {
        UserDto user = new UserDto();

        // UNSAFE: request parameter map is passed directly as the source of properties
        BeanUtilsBean beanUtils = BeanUtilsBean.getInstance();
        try {
            beanUtils.populate(user, request.getParameterMap());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Set<String> ALLOWED_PROPERTIES = Set.of("username", "email");

    /**
     * Negative sample: only whitelisted properties are populated, sensitive ones remain server-controlled.
     */
    @NegativeRuleSample(value = "java/security/external-configuration-control.yaml", id = "bean-injection")
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse resp) {
        UserDto user = new UserDto();

        Map<String, String[]> rawParams = request.getParameterMap();
        Map<String, Object> safeParams = new HashMap<>();

        for (String name : ALLOWED_PROPERTIES) {
            if (rawParams.containsKey(name)) {
                String[] values = rawParams.get(name);
                if (values != null && values.length > 0) {
                    safeParams.put(name, values[0]);
                }
            }
        }

        // admin flag is never bound from the request
        user.setAdmin(false);

        try {
            BeanUtilsBean.getInstance().populate(user, safeParams);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
