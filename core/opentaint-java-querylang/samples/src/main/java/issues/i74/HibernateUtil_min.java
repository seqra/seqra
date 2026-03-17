package issues.i74;

import java.sql.DriverManager;

public class HibernateUtil_min {
    public HibernateUtil_min() throws Exception {
        String url = "jdbc:hsqldb:benchmarkDataBase;sql.enforce_size=false";
        Object conn = DriverManager.getConnection(url, "sa", "");
    }
}
