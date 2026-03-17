package issues.i78;

public class BenchmarkTest00087_min {
    public void doPost() {
        javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("SomeCookie", "No cookie value supplied");

        cookie.setSecure(false);
    }
}
