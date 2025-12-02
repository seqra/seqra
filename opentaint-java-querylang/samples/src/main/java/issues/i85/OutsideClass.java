package issues.i85;

public class OutsideClass {
    private AsClassField insideValue;

    public OutsideClass(AsClassField value) {
        this.insideValue = value;
    }

    public String getValue() {
        return insideValue.getBadString();
    }
}
