package issues.i90;

public class CookieEater {
    private Boolean isSecure = true;
    
    public void addCookie(CookieMock cookie) {
        isSecure |= cookie.getSecure();
    }
}
