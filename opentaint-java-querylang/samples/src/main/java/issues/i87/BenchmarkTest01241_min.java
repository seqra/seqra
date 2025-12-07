package issues.i87;

import javax.servlet.http.HttpServletRequest;

public class BenchmarkTest01241_min {
    public void doPost(HttpServletRequest request) {
        String param = request.getParameter("BenchmarkTest01241");
        try {
            javax.naming.directory.SearchControls sc = new javax.naming.directory.SearchControls();
            javax.naming.directory.InitialDirContext idc =
                    new javax.naming.directory.InitialDirContext();
            javax.naming.NamingEnumeration<javax.naming.directory.SearchResult> results =
                    idc.search("ou=users,ou=system", param, new Object[]{}, sc);
        } catch (javax.naming.NamingException e) {
        }
    }
}
