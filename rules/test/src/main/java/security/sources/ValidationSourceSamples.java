package security.sources;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.opentaint.sast.test.util.PositiveRuleSample;

/**
 * Source samples for ConstraintValidator.isValid callback.
 */
public class ValidationSourceSamples implements ConstraintValidator<Override, String> {

    private DataSource dataSource;

    @Override
    @PositiveRuleSample(value = "java/security/sqli.yaml", id = "sql-injection-in-servlet-app")
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT * FROM t WHERE x = '" + value + "'");
        } catch (Exception e) {
            // ignore
        }
        return true;
    }
}
