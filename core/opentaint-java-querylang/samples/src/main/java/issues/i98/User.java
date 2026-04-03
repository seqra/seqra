package issues.i98;

public class User {
    private CleanableData data;

    public String peculiarMethod() {
        this.data = new CleanableData();
        this.data.cleanData();
        return this.data.getInfo();
    }

    public String badMethod() {
        this.data = new CleanableData();
        return this.data.getInfo();
    }
}
