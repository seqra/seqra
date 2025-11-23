package javax.servlet.http;

public class Cookie {
    private String name;
    private String value;

    private boolean secure;
    private boolean isHttpOnly = false;

    public Cookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public void setSecure(boolean flag) {
        secure = flag;
    }

    public boolean getSecure() {
        return secure;
    }

    public String getName() {
        return name;
    }

    public void setValue(String newValue) {
        value = newValue;
    }
    public String getValue() {
        return value;
    }

    public void setHttpOnly(boolean isHttpOnly) {
        this.isHttpOnly = isHttpOnly;
    }

    public boolean isHttpOnly() {
        return isHttpOnly;
    }
}
