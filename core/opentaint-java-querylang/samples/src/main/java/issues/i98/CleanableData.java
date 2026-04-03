package issues.i98;

public class CleanableData {
    public String info;

    public CleanableData() {
        this.info = "so unsafe";
    }

    public void cleanData() { }

    public String getInfo() {
        return info;
    }
}
