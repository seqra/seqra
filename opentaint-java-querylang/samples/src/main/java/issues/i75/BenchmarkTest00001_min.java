package issues.i75;

import javax.servlet.http.HttpServletResponse;

public class BenchmarkTest00001_min {

    public void doGet(HttpServletResponse response) {
        javax.servlet.http.Cookie userCookie =
                new javax.servlet.http.Cookie("BenchmarkTest00001", "FileName");

        userCookie.setSecure(true);
        response.addCookie(userCookie);
    }
}
