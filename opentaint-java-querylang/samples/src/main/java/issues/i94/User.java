package issues.i94;

public class User {
    public String vulnerableMethod(SpecificData d) {
        this.sink(d);
        return d.getBadString();
    }

    private void sink(SpecificData s) {

    }
}
