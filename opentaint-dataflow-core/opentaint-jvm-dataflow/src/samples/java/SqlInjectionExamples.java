import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@SuppressWarnings("ALL")
public class SqlInjectionExamples {

    void bad() {
        String data = System.getenv("USER");
        try (
                Connection dbConnection = DriverManager.getConnection("", "", "");
                Statement sqlStatement = dbConnection.createStatement();
        ) {
            boolean result = sqlStatement.execute("insert into users (status) values ('updated') where name='" + data + "'");

            if (result) {
                System.out.println("User '" + data + "' updated successfully");
            } else {
                System.out.println("Unable to update records for user '" + data + "'");
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e);
        } finally {
            System.out.println("OK!");
        }
    }

}
