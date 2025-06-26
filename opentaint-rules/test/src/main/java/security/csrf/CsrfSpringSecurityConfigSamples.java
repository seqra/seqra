package security.csrf;

import org.seqra.sast.test.util.NegativeRuleSample;
import org.seqra.sast.test.util.PositiveRuleSample;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Samples for csrf-disabled-in-spring rule.
 */
public class CsrfSpringSecurityConfigSamples {

    @Configuration
    @EnableWebSecurity
    public static class UnsafeCsrfDisabledConfig extends WebSecurityConfigurerAdapter {

        @Override
        @PositiveRuleSample(value = "java/security/csrf.yaml", id = "csrf-disabled-in-spring-app")
        protected void configure(HttpSecurity http) throws Exception {
            // VULNERABLE: explicitly disabling CSRF protection
            http
                .csrf().disable();
        }
    }

    @Configuration
    @EnableWebSecurity
    public static class SafeCsrfEnabledConfig extends WebSecurityConfigurerAdapter {

        @Override
        @NegativeRuleSample(value = "java/security/csrf.yaml", id = "csrf-disabled-in-spring-app")
        protected void configure(HttpSecurity http) throws Exception {
            // SAFE: CSRF is enabled (default) with typical configuration
            http
                .csrf()
                    .ignoringAntMatchers("/csrf/unsafe-endpoint")
                .and()
                .authorizeRequests()
                    .antMatchers("/public/**").permitAll()
                    .anyRequest().authenticated();
        }
    }
}
